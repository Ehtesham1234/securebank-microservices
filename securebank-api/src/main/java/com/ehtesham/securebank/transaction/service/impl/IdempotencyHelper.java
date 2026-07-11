package com.ehtesham.securebank.transaction.service.impl;

import com.ehtesham.securebank.transaction.entity.IdempotencyKey;
import com.ehtesham.securebank.transaction.repository.IdempotencyKeyRepository;
import com.ehtesham.securebank.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

@Component
public class IdempotencyHelper {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyHelper(
            IdempotencyKeyRepository idempotencyKeyRepository,
            ObjectMapper objectMapper) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes the given operation exactly once for a given
     * idempotency key + user. If this key was already used
     * successfully, returns the SAME cached response instead
     * of re-running the operation.
     */
    // IdempotencyHelper — add operationType as a parameter
    public <T> T executeIdempotently(
            String idempotencyKey,
            User user,
            String operationType,
            Class<T> responseType,
            Supplier<T> operation) {

        Optional<IdempotencyKey> existing = idempotencyKeyRepository
                .findByIdempotencyKeyAndUserAndOperationType(
                        idempotencyKey, user, operationType);

        if (existing.isPresent()) {
            try {
                return objectMapper.readValue(
                        existing.get().getResponseBody(), responseType);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(
                        "Failed to deserialize cached idempotent response", e);
            }
        }

        T result = operation.get();

        try {
            IdempotencyKey record = new IdempotencyKey();
            record.setIdempotencyKey(idempotencyKey);
            record.setUser(user);
            record.setOperationType(operationType);
            record.setResponseBody(objectMapper.writeValueAsString(result));
            record.setResponseStatus(200);
            idempotencyKeyRepository.save(record);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to cache idempotent response", e);
        }

        return result;
    }
}