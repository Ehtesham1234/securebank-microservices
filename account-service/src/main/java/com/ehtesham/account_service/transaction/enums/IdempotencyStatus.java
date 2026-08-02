package com.ehtesham.account_service.transaction.enums;

/**
 * C6 fix: tracks whether a claimed idempotency key's operation has
 * actually finished yet, so a concurrent request with the same key can be
 * told "still processing, retry" instead of racing the original request
 * or being served an incomplete/nonexistent cached response.
 */
public enum IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED
}
