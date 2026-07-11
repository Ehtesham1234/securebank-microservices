package com.ehtesham.securebank.common.exception;

public class KycAlreadySubmittedException extends RuntimeException {
    public KycAlreadySubmittedException(String message) {
        super(message);
    }
}
