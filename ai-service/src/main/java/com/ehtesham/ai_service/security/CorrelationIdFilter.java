package com.ehtesham.ai_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * NEW — was entirely missing from the original service.
 *
 * Reads (or generates) a correlation id so a single user request can be
 * traced across api-gateway -> ai-service -> account-service/loan-service/
 * securebank-api in the logs. Puts it in MDC (so logging.pattern.level in
 * application.properties can print it on every line) and echoes it back
 * as a response header for client-side debugging.
 *
 * The API gateway should be updated to forward whatever correlation id it
 * receives (or generate one at the edge) as X-Correlation-Id; this filter
 * degrades gracefully by minting its own if the header is absent, which
 * is exactly what you want when testing ai-service standalone without
 * the gateway in front of it.
 */
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        try {
            MDC.put(MDC_KEY, correlationId);
            response.setHeader(HEADER, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
