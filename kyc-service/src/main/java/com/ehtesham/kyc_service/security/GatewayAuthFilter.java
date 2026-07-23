package com.ehtesham.kyc_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class GatewayAuthFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(GatewayAuthFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Internal and actuator paths bypass auth
        if (path.startsWith("/api/v1/internal/")
                || path.startsWith("/actuator/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String userIdHeader = request.getHeader("X-User-Id");
        String roleHeader = request.getHeader("X-User-Role");
        String emailHeader = request.getHeader("X-User-Email");
        String statusHeader = request.getHeader("X-User-Status");

        if (userIdHeader == null || userIdHeader.isBlank()) {
            sendError(response, 401,
                    "Missing authentication headers. " +
                            "Request must come through the API gateway.");
            return;
        }

        try {
            Long userId = Long.parseLong(userIdHeader);

            // kyc-service enforces status differently:
            // PENDING_KYC → ALLOWED (this is why they're here)
            // SUSPENDED → BLOCKED
            // CLOSED → BLOCKED
            // ACTIVE → ALLOWED
            if (statusHeader != null) {
                switch (statusHeader) {
                    case "SUSPENDED" -> {
                        sendError(response, 403,
                                "Your account has been suspended.");
                        return;
                    }
                    case "CLOSED" -> {
                        sendError(response, 403,
                                "This account has been closed.");
                        return;
                    }
                    // PENDING_KYC and ACTIVE → allow through
                }
            }

            List<SimpleGrantedAuthority> authorities =
                    List.of(new SimpleGrantedAuthority(
                            roleHeader != null ? roleHeader : ""));

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            emailHeader, null, authorities);

            // Store userId as details — accessible via
            // SecurityUtils.getCurrentUserId()
            auth.setDetails(userId);

            SecurityContextHolder.getContext()
                    .setAuthentication(auth);

        } catch (NumberFormatException e) {
            sendError(response, 401,
                    "Invalid authentication headers.");
            return;
        } finally {
            // Always clear after request
            // Prevent ThreadLocal leakage in thread pools
        }

        filterChain.doFilter(request, response);

        // Clear after request completes
        SecurityContextHolder.clearContext();
    }

    private void sendError(HttpServletResponse response,
                           int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"status\":%d,\"message\":\"%s\"," +
                        "\"success\":false}", status, message));
    }
}