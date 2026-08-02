package com.ehtesham.ai_service.feign;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * ai-service calls account-service/loan-service directly over Feign
 * (never through api-gateway) to serve its own AI tool calls. Those are
 * ordinary, user-scoped endpoints, protected the same way as everything
 * else: a valid JWT.
 *
 * Since every service now verifies the JWT itself instead of trusting a
 * gateway-signed header, there's no special "second hop" case to handle
 * any more — this just forwards the exact Authorization header ai-service
 * itself received onto the outgoing call, and account-service/loan-service
 * verify it exactly the same way they would if it came straight from the
 * gateway. (The previous version of this class had to relay a
 * gateway-produced HMAC signature within a tight freshness window instead
 * — this is what that reasoning simplified down to.)
 */
@Configuration
public class IdentityForwardingFeignConfig {

    @Bean
    public RequestInterceptor identityForwardingInterceptor() {
        return template -> {
            HttpServletRequest inbound = currentRequest();
            if (inbound == null) {
                // No inbound request to forward from (e.g. a scheduled
                // job) — nothing to attach. Downstream will reject the
                // call, which is correct: there's no verified identity to
                // act on behalf of.
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
