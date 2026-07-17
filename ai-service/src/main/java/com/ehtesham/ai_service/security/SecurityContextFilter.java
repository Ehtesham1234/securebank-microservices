package com.ehtesham.ai_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Extracts user identity from headers set by the API Gateway.
 * The gateway validates the JWT and sets X-User-Id, X-User-Email,
 * X-User-Role before forwarding. This service trusts those headers
 * because it is on the internal network — direct external access
 * is blocked at the network level (enforce this with a network
 * policy / security group, not just by convention).
 *
 * Runs after CorrelationIdFilter (order 1) so identity-related log
 * lines are already tagged with a correlation id.
 */
@Component
@Order(2)
public class SecurityContextFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(SecurityContextFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String userIdHeader = request.getHeader("X-User-Id");
            String userEmail = request.getHeader("X-User-Email");
            String userRole = request.getHeader("X-User-Role");

            if (userIdHeader != null && !userIdHeader.isBlank()) {
                try {
                    SecurityContext ctx = SecurityContext.builder()
                            .userId(Long.parseLong(userIdHeader))
                            .userEmail(userEmail)
                            .userRole(userRole)
                            .build();
                    SecurityContextHolder.set(ctx);

                    log.debug("Security context set: userId={}, role={}",
                            userIdHeader, userRole);
                } catch (NumberFormatException e) {
                    // Don't set a context. Downstream, SecurityContextHolder.get()
                    // will throw IllegalStateException, which GlobalExceptionHandler
                    // maps to a clean 401 — rather than letting a malformed header
                    // bubble up as an uncaught exception here.
                    log.warn("X-User-Id header was present but not a valid Long: '{}'",
                            userIdHeader);
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            // Always clear — prevents ThreadLocal leakage across requests
            // in a pooled thread.
            SecurityContextHolder.clear();
        }
    }
}
