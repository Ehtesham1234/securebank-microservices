package com.ehtesham.loan_service.security;



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

        // Internal endpoints bypass all checks
        String path = request.getRequestURI();
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
            sendUnauthorized(response,
                    "Missing authentication headers. " +
                            "Requests must come through the API gateway.");
            return;
        }

        try {
            Long userId = Long.parseLong(userIdHeader);

            // Enforce user status BEFORE allowing access
            if (statusHeader != null) {
                switch (statusHeader) {
                    case "SUSPENDED" -> {
                        sendForbidden(response,
                                "Your account has been suspended. " +
                                        "Please contact support.");
                        return;
                    }
                    case "CLOSED" -> {
                        sendForbidden(response,
                                "This account has been closed.");
                        return;
                    }
                    case "PENDING_KYC" -> {
                        // PENDING_KYC users cannot access banking
                        // services — they must complete KYC first
                        // KYC routes are in kyc-service, not here
                        // So block ALL requests to account-service
                        // from PENDING_KYC users
                        sendForbidden(response,
                                "Please complete KYC verification " +
                                        "before accessing banking services.");
                        return;
                    }
                    // ACTIVE → allow through
                }
            }

            List<SimpleGrantedAuthority> authorities =
                    List.of(new SimpleGrantedAuthority(
                            roleHeader != null ? roleHeader : ""));

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            emailHeader, null, authorities);

            auth.setDetails(userId);

            SecurityContextHolder.getContext()
                    .setAuthentication(auth);

            filterChain.doFilter(request, response);

        } catch (NumberFormatException e) {
            sendUnauthorized(response,
                    "Invalid authentication headers.");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void sendUnauthorized(HttpServletResponse response,
                                  String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":401,\"message\":\"" +
                        message + "\",\"success\":false}");
    }

    private void sendForbidden(HttpServletResponse response,
                               String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":403,\"message\":\"" +
                        message + "\",\"success\":false}");
    }
}