package com.ehtesham.account_service.exception;

public class TransactionAlreadyReversedException extends RuntimeException {
    public TransactionAlreadyReversedException(String message) {
        super(message);
    }
}
