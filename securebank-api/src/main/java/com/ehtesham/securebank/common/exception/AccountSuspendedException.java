package com.ehtesham.securebank.common.exception;


public class AccountSuspendedException
        extends RuntimeException  {
    public AccountSuspendedException(String message) {
        super(message);
    }
}
