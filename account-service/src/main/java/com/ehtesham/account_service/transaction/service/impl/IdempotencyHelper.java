package com.ehtesham.account_service.transaction.service.impl;


import com.ehtesham.account_service.exception.RequestInProgressException;
import com.ehtesham.account_service.transaction.entity.IdempotencyKey;
import com.ehtesham.account_service.transaction.enums.IdempotencyStatus;
import com.ehtesham.account_service.transaction.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

@Component
public class IdempotencyHelper {

    private static final Logger log =
            LoggerFactory.getLogger(IdempotencyHelper.class);

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

        // C6 fix: claim the key FIRST, before the operation runs, by
        // attempting the INSERT immediately (saveAndFlush forces it to hit
        // the DB — and therefore the unique constraint — right here,
        // instead of at the end of some enclosing transaction). Two
        // concurrent requests with the same key can no longer both pass a
        // stale "not found yet" check and both execute the operation —
        // whichever loses the INSERT race gets a constraint violation and
        // falls through to the "someone else owns this key" branch below.
        //
        // Each repository call here is its own transaction by default
        // (Spring Data JPA's SimpleJpaRepository), same as the original
        // code — this intentionally does NOT wrap the whole method in one
        // @Transactional, since that would hold the claim row locked for
        // the full duration of the money-movement operation and block
        // every legitimate concurrent request (not just genuine retries)
        // until it committed.
        IdempotencyKey claim = new IdempotencyKey();
        claim.setIdempotencyKey(idempotencyKey);
        claim.setUserId(userId);
        claim.setOperationType(operationType);
        claim.setStatus(IdempotencyStatus.IN_PROGRESS);

        boolean claimed;
        try {
            idempotencyKeyRepository.saveAndFlush(claim);
            claimed = true;
        } catch (DataIntegrityViolationException e) {
            claimed = false;
        }

        if (!claimed) {
            return handleExistingClaim(
                    idempotencyKey, userId, operationType, responseType);
        }

        T result;
        try {
            result = operation.get();
        } catch (RuntimeException e) {
            // C6 fix: don't leave this key permanently stuck IN_PROGRESS
            // if the operation itself throws — release the claim so a
            // legitimate retry with the same key isn't locked out forever
            // by a request that never actually completed.
            idempotencyKeyRepository.deleteById(claim.getId());
            throw e;
        }

        try {
            claim.setResponseBody(objectMapper.writeValueAsString(result));
            claim.setResponseStatus(200);
            claim.setStatus(IdempotencyStatus.COMPLETED);
            idempotencyKeyRepository.save(claim);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to cache idempotent response", e);
        }

        return result;
    }

    private <T> T handleExistingClaim(
            String idempotencyKey,
            Long userId,
            String operationType,
            Class<T> responseType) {

        Optional<IdempotencyKey> existingOpt = idempotencyKeyRepository
                .findByIdempotencyKeyAndUserIdAndOperationType(
                        idempotencyKey, userId, operationType);

        if (existingOpt.isEmpty()) {
            // Should not happen — the INSERT just failed on this exact
            // unique key, so a row must exist. Fail safe rather than
            // silently re-running the operation.
            log.error("Idempotency claim for key={} userId={} op={} " +
                            "failed but no existing row was found",
                    idempotencyKey, userId, operationType);
            throw new com.ehtesham.account_service.exception.IdempotencyStateException(
                    "Could not resolve idempotency key state");
        }

        IdempotencyKey existing = existingOpt.get();

        if (existing.getStatus() == IdempotencyStatus.IN_PROGRESS) {
            throw new RequestInProgressException(
                    "A request with this idempotency key is already " +
                            "being processed. Please retry shortly.");
        }

        try {
            return objectMapper.readValue(
                    existing.getResponseBody(), responseType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to deserialize cached response", e);
        }
    }
}