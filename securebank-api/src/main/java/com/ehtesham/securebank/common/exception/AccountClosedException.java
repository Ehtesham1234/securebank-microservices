package com.ehtesham.securebank.common.exception;



public class AccountClosedException
        extends RuntimeException  {
    public AccountClosedException(String message) {
        super(message);
    }
}