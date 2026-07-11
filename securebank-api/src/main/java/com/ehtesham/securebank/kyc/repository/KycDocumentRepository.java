package com.ehtesham.securebank.kyc.repository;

import com.ehtesham.securebank.common.enums.KycStatus;
import com.ehtesham.securebank.kyc.entity.KycDocument;
import com.ehtesham.securebank.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KycDocumentRepository
        extends JpaRepository<KycDocument, Long> {

    // get all KYC docs for a user
    List<KycDocument> findByUser(User user);

    // get latest KYC doc for a user by status
    Optional<KycDocument> findByUserAndStatus(
            User user, KycStatus status);

    // get all pending KYC docs — for TELLER dashboard
    List<KycDocument> findByStatus(KycStatus status);

    // check if user already has a pending submission
    boolean existsByUserAndStatus(User user, KycStatus status);
}