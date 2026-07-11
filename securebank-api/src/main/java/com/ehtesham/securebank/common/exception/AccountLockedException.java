package com.ehtesham.securebank.common.exception;


public class AccountLockedException extends RuntimeException  {
    public AccountLockedException(String message) {
        super(message);
    }
}
