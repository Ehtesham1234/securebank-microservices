package com.ehtesham.loan_service.client;

/**
 * Thrown by UserStatusClientFallback when securebank-api can't be
 * reached to confirm current account status. Caught in
 * LoanServiceImpl.verifyLiveUserStatus() and treated as "proceed using
 * the already-verified gateway header" (fail-open) rather than blocking
 * every loan operation bank-wide on one dependency's availability — the
 * same trade-off account-service makes for deposit/withdraw/transfer.
 */
public class UserStatusCheckUnavailableException extends RuntimeException {
    public UserStatusCheckUnavailableException(String message) {
        super(message);
    }
}
