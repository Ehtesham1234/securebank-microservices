package com.ehtesham.account_service.client;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Forwards the current inbound request's Authorization header (the
 * caller's own JWT — a teller verifying KYC, a customer applying for a
 * loan, etc.) onto outgoing Feign calls to another service's
 * "/api/v1/internal/**" endpoints. The receiving service verifies that
 * JWT the exact same way it verifies any other request, and its
 * controller enforces WHO is allowed to call it (role checks, or "must
 * be your own userId") via @PreAuthorize.
 *
 * This replaces a static shared-secret API key: every internal call in
 * this codebase originates from a real authenticated request, so there's
 * always a real JWT available to forward — no separate service-identity
 * credential needed. (If a future scheduled/cron job needs to call
 * another service with no originating user request, THAT would need a
 * different mechanism — nothing here covers that case.)
 */
@Configuration
public class IdentityForwardingFeignConfig {

    @Bean
    public RequestInterceptor identityForwardingInterceptor() {
        return template -> {
            HttpServletRequest inbound = currentRequest();
            if (inbound == null) {
                return;
            }
            String authHeader = inbound.getHeader("Authorization");
            if (authHeader != null) {
                template.header("Authorization", authHeader);
            }
        };
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }
}
