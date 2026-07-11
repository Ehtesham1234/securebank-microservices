package com.ehtesham.securebank.common.exception;

import com.ehtesham.securebank.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {




    // ── Malformed request handlers (NEW — add these near the top) ──

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ErrorResponse handleMalformedJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        return ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "MALFORMED_REQUEST",
                "Request body is missing or not valid JSON",
                request.getRequestURI());
    }

    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ErrorResponse handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {

        return ErrorResponse.of(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                "UNSUPPORTED_MEDIA_TYPE",
                "Content-Type not supported for this endpoint. " +
                        "Expected: " + ex.getSupportedMediaTypes(),
                request.getRequestURI());
    }

// ── Validation ──────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.validation(
                "Input validation failed",
                request.getRequestURI(),
                errors));
    }

// ── Auth exceptions ─────────────────────────────────────────

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "CONFLICT",
                ex.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                "UNAUTHORIZED",
                ex.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpired(
            TokenExpiredException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                "TOKEN_EXPIRED",
                ex.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOtp(
            InvalidOtpException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_OTP",
                ex.getMessage(),
                request.getRequestURI()));
    }
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidRoleException.class)
    public ErrorResponse handleInvalidRole(
            InvalidRoleException ex,
            HttpServletRequest request) {
        return ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_ROLE",
                ex.getMessage(),
                request.getRequestURI());
    }

    // GlobalExceptionHandler
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(TokenReuseDetectedException.class)
    public ErrorResponse handleTokenReuseDetected(
            TokenReuseDetectedException ex,
            HttpServletRequest request) {
        return ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                "TOKEN_REUSE_DETECTED",
                ex.getMessage(),
                request.getRequestURI());
    }

// ── Account status exceptions ────────────────────────────────

    @ExceptionHandler(KycNotVerifiedException.class)
    public ResponseEntity<ErrorResponse> handleKycNotVerified(
            KycNotVerifiedException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                "KYC_NOT_VERIFIED",
                ex.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler(AccountSuspendedException.class)
    public ResponseEntity<ErrorResponse> handleAccountSuspended(
            AccountSuspendedException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                "ACCOUNT_SUSPENDED",
                ex.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler(AccountClosedException.class)
    public ResponseEntity<ErrorResponse> handleAccountClosed(
            AccountClosedException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                "ACCOUNT_CLOSED",
                ex.getMessage(),
                request.getRequestURI()));
    }
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ErrorResponse handleOptimisticLocking(
            org.springframework.orm.ObjectOptimisticLockingFailureException ex,
            HttpServletRequest request) {

        return ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "CONCURRENT_MODIFICATION",
                "This account was just modified by another request. " +
                        "Please try again.",
                request.getRequestURI());
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ErrorResponse handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {
        return ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                "RESOURCE_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI());
    }

    // GlobalExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InsufficientFundsException.class)
    public ErrorResponse handleInsufficientFunds(
            InsufficientFundsException ex,
            HttpServletRequest request) {
        return ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "INSUFFICIENT_FUNDS",
                ex.getMessage(),
                request.getRequestURI());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(TransactionAlreadyReversedException.class)
    public ErrorResponse handleTransactionAlreadyReversedException(
            TransactionAlreadyReversedException ex,
            HttpServletRequest request) {
        return ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "Transaction_Already_Reversed",
                ex.getMessage(),
                request.getRequestURI());
    }

// ── Spring Security exceptions ───────────────────────────────

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponse> handleLocked(
            LockedException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                "ACCOUNT_LOCKED",
                ex.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(
            DisabledException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                "ACCOUNT_DISABLED",
                ex.getMessage(),
                request.getRequestURI()));
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(KycAlreadySubmittedException.class)
    public ErrorResponse handleKycAlreadySubmitted(
            KycAlreadySubmittedException ex,
            HttpServletRequest request) {
        return ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "KYC_ALREADY_SUBMITTED",
                ex.getMessage(),
                request.getRequestURI());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(AccountOperationException.class)
    public ErrorResponse handleAccountOperation(
            AccountOperationException ex,
            HttpServletRequest request) {
        return ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "ACCOUNT_OPERATION_ERROR",
                ex.getMessage(),
                request.getRequestURI());
    }
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    @ExceptionHandler(RateLimitExceededException.class)
    public ErrorResponse handleRateLimitExceeded(
            RateLimitExceededException ex,
            HttpServletRequest request) {
        return ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "RATE_LIMIT_EXCEEDED",
                ex.getMessage(),
                request.getRequestURI());
    }
    @ResponseStatus(HttpStatus.LOCKED)
    @ExceptionHandler(AccountLockedException.class)
    public ErrorResponse handleAccountLocked(
            AccountLockedException ex,
            HttpServletRequest request) {
        return ErrorResponse.of(
                HttpStatus.LOCKED.value(),
                "ACCOUNT_LOCKED",
                ex.getMessage(),
                request.getRequestURI());
    }

    // GlobalExceptionHandler
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(EmailNotVerifiedException.class)
    public ErrorResponse handleEmailNotVerified(
            EmailNotVerifiedException ex,
            HttpServletRequest request) {
        return ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                "EMAIL_NOT_VERIFIED",
                ex.getMessage(),
                request.getRequestURI());
    }
// ── Generic fallback ─────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                request.getRequestURI()));
    }
}