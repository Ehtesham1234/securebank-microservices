package com.ehtesham.account_service.exception;

/**
 * Bug fix: IdempotencyHelper's handleExistingClaim() used to throw a
 * plain IllegalStateException for its "should not happen" fail-safe
 * case (an INSERT failed on the unique idempotency-key constraint, but
 * no row was found when looking it back up). That collided with
 * GlobalExceptionHandler's IllegalStateException handler — which exists
 * for SecurityUtils's "no authenticated user in context" case — and so
 * came back as a misleading 401 Unauthorized instead of a 500. This
 * gives that rare internal-consistency case its own type so it maps to
 * the correct status.
 */
public class IdempotencyStateException extends RuntimeException {
    public IdempotencyStateException(String message) {
        super(message);
    }
}
