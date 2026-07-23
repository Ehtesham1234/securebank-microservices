package com.ehtesham.account_service.transaction.repository;


import com.ehtesham.account_service.transaction.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository
        extends JpaRepository<IdempotencyKey, Long> {

    // Changed: uses Long userId instead of User entity
    Optional<IdempotencyKey> findByIdempotencyKeyAndUserIdAndOperationType(
            String idempotencyKey,
            Long userId,
            String operationType);

    Optional<IdempotencyKey> findByIdempotencyKeyAndUserId(
            String idempotencyKey, Long userId);

}