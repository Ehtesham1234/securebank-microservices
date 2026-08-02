package com.ehtesham.account_service.client;

/**
 * M5 fix: thrown by UserStatusClientFallback when securebank-api can't be
 * reached to confirm current account status. Callers decide explicitly
 * whether that means "proceed using the already-verified gateway header
 * as a fallback of last resort" or "block the operation" — see the
 * catch block around verifyLiveUserStatus() in TransactionServiceImpl for
 * the reasoning and which one this app currently chooses.
 */
public class UserStatusCheckUnavailableException extends RuntimeException {
    public UserStatusCheckUnavailableException(String message) {
        super(message);
    }
}
