package com.ehtesham.kyc_service.exception;

public class KycAlreadyExistsException extends RuntimeException {
    public KycAlreadyExistsException(String message) {
        super(message);
    }
}
