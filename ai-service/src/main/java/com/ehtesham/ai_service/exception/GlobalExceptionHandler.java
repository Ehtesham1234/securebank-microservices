package com.ehtesham.ai_service.exception;

import com.ehtesham.ai_service.dto.ErrorResponse;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

/**
 * NEW — the original service had no exception handling layer at all.
 * Without this, three real problems would show up under normal use:
 *
 *   1. @Valid failures on ChatRequest (blank/too-long question) fell
 *      through to Spring's default error handling, returning a generic,
 *      unstructured 400 body the frontend can't parse consistently.
 *   2. A missing/invalid X-User-Id header (e.g. calling ai-service
 *      directly while testing, bypassing the gateway) causes
 *      SecurityContextHolder.get() to throw IllegalStateException,
 *      which — uncaught — surfaces as a 500 with a stack trace in the
 *      response body. That's an information leak and the wrong status
 *      code; it should be a clean 401.
 *   3. The new @RateLimiter on AiController throws RequestNotPermitted
 *      once the limit is hit; uncaught, that's also an unstructured 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();

        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("VALIDATION_FAILED")
                .message("One or more fields are invalid")
                .details(details)
                .build());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleMissingSecurityContext(IllegalStateException ex) {
        log.warn("Request rejected — no verified security context: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("UNAUTHENTICATED")
                .message("This request did not carry a verified identity. " +
                        "Requests must go through the API gateway.")
                .details(List.of())
                .build());
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ErrorResponse> handleRateLimited(RequestNotPermitted ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("RATE_LIMITED")
                .message("Too many AI requests. Please slow down and try again shortly.")
                .details(List.of())
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);

        return ResponseEntity.internalServerError().body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("INTERNAL_ERROR")
                .message("Something went wrong on our side. Please try again.")
                .details(List.of())
                .build());
    }
}
