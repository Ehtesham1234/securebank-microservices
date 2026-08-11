package com.ehtesham.kyc_service.entity;


import com.ehtesham.kyc_service.enums.KycDocumentType;
import com.ehtesham.kyc_service.enums.KycStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
@Getter
@Setter
@Entity
@Table(name = "kyc_documents")
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private KycDocumentType documentType;

    @Column(name = "document_number", nullable = false, length = 50)
    private String documentNumber;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycStatus status = KycStatus.PENDING;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "verified_by")
    private Long verifiedBy;  

    // Bug fix: every other staff-decision entity in this codebase (Loan,
    // Account, Card) uses optimistic locking to catch two staff members
    // acting on the same record at once. This one didn't — two tellers
    // hitting verify and reject on the same PENDING submission near-
    // simultaneously could both pass the status==PENDING check before
    // either commits, and whichever writes last silently wins with no
    // error to the loser. @Version makes the second writer's commit fail
    // with an ObjectOptimisticLockingFailureException instead (handled
    // as a clean 409 — see GlobalExceptionHandler) so staff get an
    // honest "someone else already acted on this" instead of a false
    // "success".
    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
