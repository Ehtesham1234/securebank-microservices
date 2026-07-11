package com.ehtesham.securebank.security.filter;

import com.ehtesham.securebank.common.enums.UserStatus;
import com.ehtesham.securebank.security.service.CustomUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class UserStatusFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    public UserStatusFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String requestURI = request.getRequestURI();

        // read the SAME object JwtAuthenticationFilter already loaded
        // no new DB query happens here
        Object attribute = request.getAttribute(
                JwtAuthenticationFilter.USER_DETAILS_ATTRIBUTE);

        if (!(attribute instanceof CustomUserPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (principal.getUserStatus() == UserStatus.PENDING_KYC
                && !requestURI.startsWith("/api/v1/kyc/")) {
            writeErrorResponse(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "KYC_NOT_VERIFIED",
                    "Please complete KYC verification first",
                    requestURI);
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/v1/auth/");
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            int status,
            String error,
            String message,
            String path) throws IOException {

        response.setStatus(status);
        response.setContentType("application/json");

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        body.put("path", path);
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("validationErrors", null);

        response.getWriter()
                .write(objectMapper.writeValueAsString(body));
    }
}