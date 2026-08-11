package com.ehtesham.account_service.exception;

import com.ehtesham.account_service.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            AccountNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        404,
                        "ACCOUNT_NOT_FOUND",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResource(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        404,
                        "RESOURCE_NOT_FOUND",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(AccountOperationException.class)
    public ResponseEntity<ErrorResponse> handleOperation(
            AccountOperationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        400,
                        "ACCOUNT_OPERATION_FAILED",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleFunds(
            InsufficientFundsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        400,
                        "INSUFFICIENT_FUNDS",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(TransactionAlreadyReversedException.class)
    public ResponseEntity<ErrorResponse> handleReversed(
            TransactionAlreadyReversedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        409,
                        "TRANSACTION_ALREADY_REVERSED",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    // C6 fix: another request with the same idempotency key is still
    // in flight — tell the caller to retry rather than treat it as a
    // failure or, worse, silently re-run the operation.
    @ExceptionHandler(RequestInProgressException.class)
    public ResponseEntity<ErrorResponse> handleInProgress(
            RequestInProgressException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        409,
                        "REQUEST_IN_PROGRESS",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    // M2 fix: Account correctly uses @Version for optimistic locking
    // (prevents the classic lost-update/overdraft race on concurrent
    // balance updates), but a genuine conflict was falling through to
    // the generic Exception.class handler below and returning a bare
    // 500 — no data-integrity risk (the @Version protection already
    // worked), but the client had no way to tell "retry this" apart
    // from "something's actually broken".
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Optimistic lock conflict on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        409,
                        "CONCURRENT_UPDATE",
                        "This record was updated by another request. Please retry.",
                        request.getRequestURI()
                ));
    }

    // Backs the idempotency approach in EMI debit (C4) and loan
    // disbursement (H2): a genuine race on the same deterministic
    // transactionRef hits the DB's UNIQUE constraint, which safely
    // rolls back whichever request lost (undoing its balance change) —
    // this turns that into a clear 409 instead of a raw 500.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        409,
                        "CONFLICTING_OPERATION",
                        "A conflicting operation occurred — this may already " +
                                "have been processed. Please check before retrying.",
                        request.getRequestURI()
                ));
    }

    // L1 fix: too many wrong CVV guesses against one card.
    @ExceptionHandler(CvvVerificationLockedException.class)
    public ResponseEntity<ErrorResponse> handleCvvLocked(
            CvvVerificationLockedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.of(
                        429,
                        "CVV_VERIFICATION_LOCKED",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    // M5 fix: live status check found the account no longer active.
    @ExceptionHandler(AccountSuspendedException.class)
    public ResponseEntity<ErrorResponse> handleSuspended(
            AccountSuspendedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(
                        403,
                        "ACCOUNT_SUSPENDED",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (msg1, msg2) -> msg1
                ));

        return ResponseEntity.badRequest()
                .body(ErrorResponse.validation(
                        "Validation failed",
                        request.getRequestURI(),
                        errors
                ));
    }

    // H1 fix: @Validated + @RequestParam/@PathVariable constraints
    // (e.g. the new @DecimalMin on CardController's spend/pay-bill
    // amount) throw THIS, not MethodArgumentNotValidException — that
    // one's only for @RequestBody @Valid. Without this handler, a
    // rejected negative amount would 500 instead of a clean 400.
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            jakarta.validation.ConstraintViolationException ex, HttpServletRequest request) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));

        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        400,
                        "VALIDATION_FAILED",
                        message,
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleState(
            IllegalStateException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(
                        401,
                        "UNAUTHORIZED",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    // Bug fix: this used to be a plain IllegalStateException, which was
    // being caught by the handler above (meant for SecurityUtils's "no
    // authenticated user" case) and returned as a misleading 401. This
    // is a genuine internal-consistency edge case — an idempotency-key
    // INSERT failed on the unique constraint but the row wasn't found on
    // lookup — and belongs as a 500.
    @ExceptionHandler(com.ehtesham.account_service.exception.IdempotencyStateException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyState(
            com.ehtesham.account_service.exception.IdempotencyStateException ex,
            HttpServletRequest request) {
        log.error("Idempotency state inconsistency at {}: {}",
                request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        500,
                        "INTERNAL_SERVER_ERROR",
                        "Could not process this request. Please try again.",
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        500,
                        "INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred",
                        request.getRequestURI()
                ));
    }

}
