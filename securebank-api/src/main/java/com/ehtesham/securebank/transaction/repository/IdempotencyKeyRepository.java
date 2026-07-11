package com.ehtesham.securebank.transaction.repository;

import com.ehtesham.securebank.transaction.entity.IdempotencyKey;
import com.ehtesham.securebank.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository
        extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByIdempotencyKeyAndUser(
            String idempotencyKey, User user);
    Optional<IdempotencyKey> findByIdempotencyKeyAndUserAndOperationType(
            String idempotencyKey, User user, String operationType);
}