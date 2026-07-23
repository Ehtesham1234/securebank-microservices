package com.ehtesham.kyc_service.exception;

public class KycOperationException extends RuntimeException {
    public KycOperationException(String message) {
        super(message);
    }
}
