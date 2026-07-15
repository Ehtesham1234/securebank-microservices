package com.ehtesham.loan_service.exception;

public class LoanOperationException extends RuntimeException {
    public LoanOperationException(String message) {
        super(message);
    }
}
