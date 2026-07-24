package com.ehtesham.account_service.transaction.repository;


import com.ehtesham.account_service.transaction.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyKeyRepository
        extends JpaRepository<IdempotencyKey, Long> {

    // Changed: uses Long userId instead of User entity
    Optional<IdempotencyKey> findByIdempotencyKeyAndUserIdAndOperationType(
            String idempotencyKey,
            Long userId,
            String operationType);
    @Modifying
    @Query("DELETE FROM IdempotencyKey i " +
            "WHERE i.createdAt < :cutoff")
    int deleteByCreatedAtBefore(LocalDateTime cutoff);

}