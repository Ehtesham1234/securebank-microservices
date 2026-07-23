package com.ehtesham.kyc_service.service.impl;

import com.ehtesham.kyc_service.audit.annotation.Auditable;
import com.ehtesham.kyc_service.client.AccountServiceClient;
import com.ehtesham.kyc_service.client.UserServiceClient;
import com.ehtesham.kyc_service.dto.InternalUserResponse;
import com.ehtesham.kyc_service.dto.KycRejectRequest;
import com.ehtesham.kyc_service.dto.KycResponse;
import com.ehtesham.kyc_service.dto.KycSubmitRequest;
import com.ehtesham.kyc_service.entity.KycDocument;
import com.ehtesham.kyc_service.enums.KycStatus;
import com.ehtesham.kyc_service.exception.KycAlreadyExistsException;
import com.ehtesham.kyc_service.exception.KycOperationException;
import com.ehtesham.kyc_service.exception.ResourceNotFoundException;
import com.ehtesham.kyc_service.notification.KycEventPublisher;
import com.ehtesham.kyc_service.repository.KycDocumentRepository;
import com.ehtesham.kyc_service.security.SecurityUtils;
import com.ehtesham.kyc_service.service.KycService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KycServiceImpl implements KycService {

    private static final Logger log =
            LoggerFactory.getLogger(KycServiceImpl.class);

    private final KycDocumentRepository kycRepository;
    private final SecurityUtils securityUtils;
    private final UserServiceClient userServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final KycEventPublisher kycEventPublisher;

    @Value("${file.upload.path:uploads}")
    private String uploadPath;

    public KycServiceImpl(
            KycDocumentRepository kycRepository,
            SecurityUtils securityUtils,
            UserServiceClient userServiceClient,
            AccountServiceClient accountServiceClient,
            KycEventPublisher kycEventPublisher) {
        this.kycRepository = kycRepository;
        this.securityUtils = securityUtils;
        this.userServiceClient = userServiceClient;
        this.accountServiceClient = accountServiceClient;
        this.kycEventPublisher = kycEventPublisher;
    }

    @Override
    @Transactional
    public KycResponse submitKyc(KycSubmitRequest request,
                                 MultipartFile file) {

        Long userId = securityUtils.getCurrentUserId();

        // Block resubmission if already pending
        if (kycRepository.existsByUserIdAndStatus(
                userId, KycStatus.PENDING)) {
            throw new KycAlreadyExistsException(
                    "You already have a pending KYC submission.");
        }

        // Block resubmission if already verified
        if (kycRepository.existsByUserIdAndStatus(
                userId, KycStatus.VERIFIED)) {
            throw new KycAlreadyExistsException(
                    "Your KYC is already verified.");
        }

        String filePath = saveFile(file, userId);

        KycDocument doc = new KycDocument();
        doc.setUserId(userId);
        doc.setDocumentType(request.getDocumentType());
        doc.setDocumentNumber(request.getDocumentNumber());
        doc.setFilePath(filePath);
        doc.setStatus(KycStatus.PENDING);

        KycDocument saved = kycRepository.save(doc);

        // Notify via Kafka — email comes from gateway header
        String userEmail = securityUtils.getCurrentUserEmail();
        kycEventPublisher.publishKycSubmitted(userId, userEmail);

        log.info("KYC submitted: userId={}, docType={}",
                userId, request.getDocumentType());

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public KycResponse getMyKycStatus() {

        Long userId = securityUtils.getCurrentUserId();

        KycDocument doc = kycRepository
                .findByUserId(userId)
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No KYC submission found. " +
                                        "Please submit your KYC documents."));

        return mapToResponse(doc);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KycResponse> getPendingKycList() {
        return kycRepository
                .findByStatus(KycStatus.PENDING)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Auditable(action = "KYC_VERIFY")
    @Override
    @Transactional
    public KycResponse verifyKyc(Long kycId) {

        Long tellerUserId = securityUtils.getCurrentUserId();
        KycDocument doc = getKycDocument(kycId);

        if (doc.getStatus() != KycStatus.PENDING) {
            throw new KycOperationException(
                    "This KYC document has already been " +
                            doc.getStatus().name().toLowerCase() +
                            " and cannot be processed again.");
        }

        doc.setStatus(KycStatus.VERIFIED);
        doc.setVerifiedBy(tellerUserId);

        Long customerId = doc.getUserId();
        String customerEmail = ""; // fetched below

        // Step 1: Activate user in securebank-api
        // This changes their status from PENDING_KYC to ACTIVE
        // so their next request gets a valid ACTIVE JWT
        try {
            userServiceClient.activateUser(customerId);
            log.info("User activated: userId={}", customerId);
        } catch (Exception e) {
            throw new KycOperationException(
                    "Failed to activate user account. " +
                            "Please try again: " + e.getMessage());
        }

        // Step 2: Create savings account + debit card
        // in account-service via internal endpoint
        try {
            // We get name from KYC document number is not enough
            // Use a placeholder — in production, kyc-service
            // would have called securebank-api for user details
            // For now we pass userId and let account-service
            // use it — the card holder name will be updated
            // by kyc-service calling user internal endpoint
            var setup = accountServiceClient.kycSetup(
                    customerId,
                    "Customer",   // placeholder
                    String.valueOf(customerId));
            log.info("Account created for userId={}, " +
                            "accountNumber={}", customerId,
                    setup.getAccountNumber());
        } catch (Exception e) {
            // COMPENSATING TRANSACTION:
            // Account creation failed but user is already activated.
            // In a full Saga we'd publish a compensation event.
            // For now log and surface the error.
            log.error("Account setup failed for userId={}: {}",
                    customerId, e.getMessage());
            throw new KycOperationException(
                    "Account setup failed after KYC verification. " +
                            "User is activated but account creation failed. " +
                            "Please contact support.");
        }

        kycRepository.save(doc);

        // Step 3: Notify customer via Kafka
        // Email comes from gateway header set during this request
        // (the teller's email — not ideal for customer notification)
        // In production: call securebank-api internal/users/{id}
        // to get customer email. For now use userId as key.
        kycEventPublisher.publishKycVerified(
                customerId, customerEmail);

        log.info("KYC verified: kycId={}, customerId={}, " +
                "tellerUserId={}", kycId, customerId, tellerUserId);
        // After getting the kycDocument, fetch user details:
                InternalUserResponse customer;
                try {
                    customer = userServiceClient.getUserById(customerId);
                } catch (Exception e) {
                    throw new KycOperationException(
                            "Could not fetch customer details: " + e.getMessage());
                }

        // Then use real name in account setup:
                var setup = accountServiceClient.kycSetup(
                        customerId,
                        customer.getFirstName(),
                        customer.getLastName());

        // And real email for notification:
                kycEventPublisher.publishKycVerified(
                        customerId, customer.getEmail());
        return mapToResponse(doc);
    }

    @Auditable(action = "KYC_REJECT")
    @Override
    @Transactional
    public KycResponse rejectKyc(Long kycId,
                                 KycRejectRequest request) {

        Long tellerUserId = securityUtils.getCurrentUserId();
        KycDocument doc = getKycDocument(kycId);

        if (doc.getStatus() != KycStatus.PENDING) {
            throw new KycOperationException(
                    "This KYC document has already been " +
                            doc.getStatus().name().toLowerCase() +
                            " and cannot be processed again.");
        }

        doc.setStatus(KycStatus.REJECTED);
        doc.setRejectionReason(request.getReason());
        doc.setVerifiedBy(tellerUserId);
        kycRepository.save(doc);

        kycEventPublisher.publishKycRejected(
                doc.getUserId(), "", request.getReason());
        // Fetch customer email for rejection notification:
        try {
            InternalUserResponse customer =
                    userServiceClient.getUserById(doc.getUserId());
            kycEventPublisher.publishKycRejected(
                    doc.getUserId(), customer.getEmail(),
                    request.getReason());
        } catch (Exception e) {
            log.warn("Could not fetch customer email for rejection " +
                    "notification: {}", e.getMessage());
            kycEventPublisher.publishKycRejected(
                    doc.getUserId(), "", request.getReason());
        }

        log.info("KYC rejected: kycId={}, userId={}, reason={}",
                kycId, doc.getUserId(), request.getReason());


        return mapToResponse(doc);
    }

    // ── Private helpers ───────────────────────────────────────────

    private KycDocument getKycDocument(Long id) {
        return kycRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "KYC document not found: " + id));
    }

    private String saveFile(MultipartFile file, Long userId) {
        try {
            Path uploadDir = Paths.get(
                    uploadPath, "kyc", userId.toString());
            Files.createDirectories(uploadDir);

            String original = file.getOriginalFilename();
            String extension = (original != null
                    && original.contains("."))
                    ? original.substring(
                    original.lastIndexOf("."))
                    : "";
            String filename = UUID.randomUUID() + extension;

            Path filePath = uploadDir.resolve(filename);
            Files.write(filePath, file.getBytes());

            return filePath.toString();

        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to save file: " + e.getMessage());
        }
    }

    private KycResponse mapToResponse(KycDocument doc) {
        return KycResponse.builder()
                .id(doc.getId())
                .userId(doc.getUserId())
                .documentType(doc.getDocumentType())
                .documentNumber(doc.getDocumentNumber())
                .status(doc.getStatus())
                .rejectionReason(doc.getRejectionReason())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}