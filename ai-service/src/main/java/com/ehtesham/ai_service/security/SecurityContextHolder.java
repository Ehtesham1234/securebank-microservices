package com.ehtesham.ai_service.security;

/**
 * Thread-local holder for the verified SecurityContext.
 * Set once per request from gateway headers, never from model output.
 * Cleared after each request to prevent leakage across a pooled thread.
 */
public class SecurityContextHolder {

    private static final ThreadLocal<SecurityContext> CONTEXT = new ThreadLocal<>();

    public static void set(SecurityContext ctx) {
        CONTEXT.set(ctx);
    }

    public static SecurityContext get() {
        SecurityContext ctx = CONTEXT.get();
        if (ctx == null) {
            throw new IllegalStateException(
                    "No security context set for this request. " +
                            "Ensure the request came through the API gateway.");
        }
        return ctx;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
