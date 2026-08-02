package com.ehtesham.account_service.exception;

/**
 * M5 fix: thrown when a live check (not just the JWT-derived header) shows
 * the acting user's status no longer permits the requested operation —
 * e.g. an admin suspended the account after the current JWT was issued.
 */
public class AccountSuspendedException extends RuntimeException {
    public AccountSuspendedException(String message) {
        super(message);
    }
}
