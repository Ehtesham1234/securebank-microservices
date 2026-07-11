package com.ehtesham.securebank.common.exception;

public class AccountOperationException extends RuntimeException {
    public AccountOperationException(String message) {
        super(message);
    }
}
