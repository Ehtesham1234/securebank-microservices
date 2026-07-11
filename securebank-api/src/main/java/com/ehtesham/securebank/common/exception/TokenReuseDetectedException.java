package com.ehtesham.securebank.common.exception;

public class TokenReuseDetectedException extends RuntimeException {
    public TokenReuseDetectedException(String message) {
        super(message);
    }
}
