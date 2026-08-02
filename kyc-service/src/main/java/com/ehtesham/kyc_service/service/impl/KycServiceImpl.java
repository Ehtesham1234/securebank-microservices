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
import java.util.Map;
import java.util.Set;
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

    // M3 fix: whitelist by ACTUAL content (magic bytes), not by trusting
    // the client-supplied filename extension or Content-Type header —
    // both are attacker-controlled. Extension is still checked too, but
    // only as a secondary, cheap rejection — the magic-byte check is what
    // actually decides whether a file is what it claims to be.
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of(".pdf", ".jpg", ".jpeg", ".png");

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10MB

    private static final Map<String, byte[]> MAGIC_BYTES = Map.of(
            "PDF", new byte[]{0x25, 0x50, 0x44, 0x46},                 // %PDF
            "JPEG", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, // JPEG SOI
            "PNG", new byte[]{
                    (byte) 0x89, 0x50, 0x4E, 0x47,
                    0x0D, 0x0A, 0x1A, 0x0A}                            // PNG signature
    );

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

        // Step 2: fetch the real customer details, then create the
        // savings account + debit card in account-service via internal
        // endpoint using the real name — and notify via Kafka using the
        // real email. (H1 fix: this used to run AFTER a first pass that
        // called kycSetup with a placeholder name/email and published a
        // premature event — leftover from a merge. Since createSavingsAccount
        // and createDebitCard are correctly idempotent, that first call
        // silently "won" — it created the account/card, and this second,
        // correct call just found them already there and never updated the
        // name. Every customer ended up with a card permanently labeled
        // "Customer <userId>". There is now only one call to each.)
        InternalUserResponse customer;
        try {
            customer = userServiceClient.getUserById(customerId);
        } catch (Exception e) {
            throw new KycOperationException(
                    "Could not fetch customer details: " + e.getMessage());
        }

        try {
            var setup = accountServiceClient.kycSetup(
                    customerId,
                    customer.getFirstName(),
                    customer.getLastName());
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

        // Step 3: notify customer via Kafka, using their real email —
        // exactly once.
        kycEventPublisher.publishKycVerified(
                customerId, customer.getEmail());

        log.info("KYC verified: kycId={}, customerId={}, " +
                "tellerUserId={}", kycId, customerId, tellerUserId);

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

        // Fetch customer email ONCE — publish ONCE
        String customerEmail = "";
        try {
            InternalUserResponse customer =
                    userServiceClient.getUserById(doc.getUserId());
            customerEmail = customer.getEmail();
        } catch (Exception e) {
            log.warn("Could not fetch customer email for " +
                    "rejection notification: {}", e.getMessage());
        }

        kycEventPublisher.publishKycRejected(
                doc.getUserId(), customerEmail,
                request.getReason());

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
            validateKycFile(file);

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

    // M3 fix: reject anything that isn't actually a PDF/JPEG/PNG before it
    // ever touches disk. A malicious .html/.svg (stored XSS against
    // whichever teller opens it later) or an executable renamed with an
    // image extension both fail this check regardless of what the
    // filename or Content-Type header claims.
    private void validateKycFile(MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new KycOperationException(
                    "No file was uploaded.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new KycOperationException(
                    "File exceeds the 10MB size limit.");
        }

        String original = file.getOriginalFilename();
        String extension = (original != null && original.contains("."))
                ? original.substring(
                        original.lastIndexOf(".")).toLowerCase()
                : "";

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new KycOperationException(
                    "Unsupported file type. Only PDF, JPG, and PNG " +
                            "documents are accepted.");
        }

        byte[] header = new byte[8];
        int read;
        try (var in = file.getInputStream()) {
            read = in.readNBytes(header, 0, header.length);
        }

        boolean matchesKnownType =
                startsWith(header, read, MAGIC_BYTES.get("PDF"))
                        || startsWith(header, read, MAGIC_BYTES.get("JPEG"))
                        || startsWith(header, read, MAGIC_BYTES.get("PNG"));

        if (!matchesKnownType) {
            throw new KycOperationException(
                    "File content does not match a supported document " +
                            "type (PDF, JPG, or PNG). The file may be " +
                            "corrupted or mislabeled.");
        }
    }

    private boolean startsWith(byte[] data, int dataLength, byte[] prefix) {
        if (dataLength < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
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