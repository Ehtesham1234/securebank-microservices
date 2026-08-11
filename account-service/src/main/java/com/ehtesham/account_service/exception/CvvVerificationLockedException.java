package com.ehtesham.account_service.exception;

/**
 * L1 fix: thrown when too many wrong CVV guesses have been made against
 * a card in verifyCvv() — independent of the CVV itself never being
 * stored (C5), this protects against brute-forcing the 3-digit space if
 * a session is ever compromised without the physical card.
 */
public class CvvVerificationLockedException extends RuntimeException {
    public CvvVerificationLockedException(String message) {
        super(message);
    }
}
