package com.ehtesham.securebank.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final boolean success = false;
    private final int status;
    private final String error;
    private final String message;
    private final String path;
    private final LocalDateTime timestamp;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Map<String, String> validationErrors;

    private ErrorResponse(int status, String error,
                          String message, String path,
                          Map<String, String> validationErrors) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.timestamp = LocalDateTime.now();
        this.validationErrors = validationErrors;
    }

    public static ErrorResponse of(int status, String error,
                                   String message, String path) {
        return new ErrorResponse(status, error, message, path, null);
    }

    public static ErrorResponse validation(String message, String path,
                                           Map<String, String> validationErrors) {
        return new ErrorResponse(400, "VALIDATION_FAILED",
                message, path, validationErrors);
    }
}