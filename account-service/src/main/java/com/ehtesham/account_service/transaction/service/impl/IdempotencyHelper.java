package com.ehtesham.account_service.transaction.service.impl;


import com.ehtesham.account_service.transaction.entity.IdempotencyKey;
import com.ehtesham.account_service.transaction.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    // Changed: takes Long userId instead of User entity
    public <T> T executeIdempotently(
            String idempotencyKey,
            Long userId,
            String operationType,
            Class<T> responseType,
            Supplier<T> operation) {

        Optional<IdempotencyKey> existing =
                idempotencyKeyRepository
                        .findByIdempotencyKeyAndUserIdAndOperationType(
                                idempotencyKey, userId, operationType);

        if (existing.isPresent()) {
            try {
                return objectMapper.readValue(
                        existing.get().getResponseBody(),
                        responseType);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(
                        "Failed to deserialize cached response", e);
            }
        }

        T result = operation.get();

        try {
            IdempotencyKey record = new IdempotencyKey();
            record.setIdempotencyKey(idempotencyKey);
            record.setUserId(userId);
            record.setOperationType(operationType);
            record.setResponseBody(
                    objectMapper.writeValueAsString(result));
            record.setResponseStatus(200);
            idempotencyKeyRepository.save(record);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to cache idempotent response", e);
        }

        return result;
    }
}