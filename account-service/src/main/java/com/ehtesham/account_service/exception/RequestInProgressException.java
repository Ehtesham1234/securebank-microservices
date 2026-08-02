package com.ehtesham.account_service.exception;

/**
 * C6 fix: thrown when a request arrives with an idempotency key that
 * another in-flight request already claimed and hasn't finished
 * processing yet. The caller should retry shortly rather than assume
 * failure — the original request is still running.
 */
public class RequestInProgressException extends RuntimeException {
    public RequestInProgressException(String message) {
        super(message);
    }
}
