package com.ehtesham.kyc_service.repository;

import com.ehtesham.kyc_service.entity.KycDocument;
import com.ehtesham.kyc_service.enums.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KycDocumentRepository
        extends JpaRepository<KycDocument, Long> {

    // userId not User entity — JPA derives query from field name
    List<KycDocument> findByUserId(Long userId);

    Optional<KycDocument> findByUserIdAndStatus(
            Long userId, KycStatus status);

    List<KycDocument> findByStatus(KycStatus status);

    boolean existsByUserIdAndStatus(
            Long userId, KycStatus status);
}