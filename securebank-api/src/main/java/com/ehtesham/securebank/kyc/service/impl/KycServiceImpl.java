package com.ehtesham.securebank.kyc.service.impl;

import com.ehtesham.securebank.account.dto.AccountResponse;
import com.ehtesham.securebank.account.entity.Account;
import com.ehtesham.securebank.account.repository.AccountRepository;
import com.ehtesham.securebank.account.service.AccountService;
import com.ehtesham.securebank.audit.annotation.Auditable;
import com.ehtesham.securebank.card.service.CardService;
import com.ehtesham.securebank.common.enums.KycStatus;
import com.ehtesham.securebank.common.enums.UserStatus;
import com.ehtesham.securebank.common.exception.AccountOperationException;
import com.ehtesham.securebank.common.exception.KycNotVerifiedException;
import com.ehtesham.securebank.common.exception.ResourceNotFoundException;
import com.ehtesham.securebank.kyc.dto.KycRejectRequest;
import com.ehtesham.securebank.kyc.dto.KycResponse;
import com.ehtesham.securebank.kyc.dto.KycSubmitRequest;
import com.ehtesham.securebank.kyc.entity.KycDocument;
import com.ehtesham.securebank.kyc.repository.KycDocumentRepository;
import com.ehtesham.securebank.kyc.service.KycService;

import com.ehtesham.securebank.notification.publisher.NotificationEventPublisher;
import com.ehtesham.securebank.user.entity.User;
import com.ehtesham.securebank.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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

    private final KycDocumentRepository kycDocumentRepository;
    private final UserRepository userRepository;
//    private final EmailService emailService;
    private final NotificationEventPublisher notificationPublisher;
    private final AccountService accountService;
    private final CardService cardService;
    private final AccountRepository accountRepository;
    @Value("${file.upload.path}")
    private String uploadPath;

    public KycServiceImpl(
            KycDocumentRepository kycDocumentRepository,
            UserRepository userRepository,
//            EmailService emailService,
            NotificationEventPublisher notificationPublisher, AccountService accountService, CardService cardService, AccountRepository accountRepository) {
        this.kycDocumentRepository = kycDocumentRepository;
        this.userRepository = userRepository;
        this.notificationPublisher = notificationPublisher;
//        this.emailService = emailService;
        this.accountService = accountService;
        this.cardService = cardService;
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional
    public KycResponse submitKyc(
            KycSubmitRequest request,
            MultipartFile file,
            String email) {

        User user = getUser(email);

        // check if already has pending or verified KYC
        if (kycDocumentRepository.existsByUserAndStatus(
                user, KycStatus.PENDING)) {
            throw new KycNotVerifiedException(
                    "You already have a pending KYC submission");
        }

        if (kycDocumentRepository.existsByUserAndStatus(
                user, KycStatus.VERIFIED)) {
            throw new KycNotVerifiedException(
                    "Your KYC is already verified");
        }

        // save file to disk
        String filePath = saveFile(file, user.getId());

        // create KYC document
        KycDocument kycDocument = new KycDocument();
        kycDocument.setUser(user);
        kycDocument.setDocumentType(request.getDocumentType());
        kycDocument.setDocumentNumber(request.getDocumentNumber());
        kycDocument.setFilePath(filePath);
        kycDocument.setStatus(KycStatus.PENDING);

        KycDocument saved = kycDocumentRepository.save(kycDocument);

        // notify teller via email
//        emailService.sendKycSubmissionNotification(user.getEmail());
        notificationPublisher.publishKycSubmission(user.getEmail());

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public KycResponse getMyKycStatus(String email) {

        User user = getUser(email);

        KycDocument kycDocument = kycDocumentRepository
                .findByUser(user)
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No KYC submission found"));

        return mapToResponse(kycDocument);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KycResponse> getPendingKycList() {

        return kycDocumentRepository
                .findByStatus(KycStatus.PENDING)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Auditable(action = "KYC_VERIFY")
    @Override
    @Transactional
    public KycResponse verifyKyc(Long kycId, String tellerEmail) {

        KycDocument kycDocument = getKycDocument(kycId);

        if (kycDocument.getStatus() != KycStatus.PENDING) {
            throw new AccountOperationException(
                    "This KYC document has already been " +
                            kycDocument.getStatus().name().toLowerCase() +
                            " and cannot be processed again.");
        }
        User teller = getUser(tellerEmail);

        // update KYC status
        kycDocument.setStatus(KycStatus.VERIFIED);
        kycDocument.setVerifiedBy(teller);

        // activate user
        User customer = kycDocument.getUser();
        customer.setUserStatus(UserStatus.ACTIVE);
        userRepository.save(customer);

        // auto-create SAVINGS account
        AccountResponse savingsAccountResponse = accountService.createSavingsAccount(customer);

        Account savingsAccount = accountRepository
                .findById(savingsAccountResponse.getId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Failed to load created savings account"));

        cardService.createDebitCard(customer, savingsAccount);
        KycDocument saved = kycDocumentRepository.save(kycDocument);
        // notify customer
//        emailService.sendKycVerifiedNotification(
//                customer.getEmail());
        notificationPublisher.publishKycVerified(customer.getEmail());

        return mapToResponse(saved);
    }

    @Auditable(action = "KYC_REJECT")
    @Override
    @Transactional
    public KycResponse rejectKyc(
            Long kycId,
            KycRejectRequest request,
            String tellerEmail) {

        KycDocument kycDocument = getKycDocument(kycId);

        if (kycDocument.getStatus() != KycStatus.PENDING) {
            throw new AccountOperationException(
                    "This KYC document has already been " +
                            kycDocument.getStatus().name().toLowerCase() +
                            " and cannot be processed again.");
        }

        User teller = getUser(tellerEmail);

        kycDocument.setStatus(KycStatus.REJECTED);
        kycDocument.setRejectionReason(request.getReason());
        kycDocument.setVerifiedBy(teller);

        KycDocument saved = kycDocumentRepository.save(kycDocument);

        // notify customer with rejection reason
//        emailService.sendKycRejectedNotification(
//                kycDocument.getUser().getEmail(),
//                request.getReason());
        notificationPublisher.publishKycRejected(kycDocument.getUser().getEmail(),
                request.getReason());

        return mapToResponse(saved);
    }

    // ── Private helpers ──────────────────────────────────────────

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found: " + email));
    }

    private KycDocument getKycDocument(Long id) {
        return kycDocumentRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "KYC document not found"));
    }
    private String saveFile(MultipartFile file, Long userId) {

        try {
            // create directory if not exists
            Path uploadDir = Paths.get(uploadPath, "kyc",
                    userId.toString());
            Files.createDirectories(uploadDir);

            // generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null
                    && originalFilename.contains(".")
                    ? originalFilename.substring(
                    originalFilename.lastIndexOf("."))
                    : "";
            String filename = UUID.randomUUID() + extension;

            // save file
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
                .userId(doc.getUser().getId())
                .userEmail(doc.getUser().getEmail())
                .documentType(doc.getDocumentType())
                .documentNumber(doc.getDocumentNumber())
                .status(doc.getStatus())
                .rejectionReason(doc.getRejectionReason())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}