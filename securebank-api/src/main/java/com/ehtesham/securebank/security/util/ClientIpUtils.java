package com.ehtesham.securebank.security.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * M2 fix: gives LoginAttemptServiceImpl an IP-based signal to correlate
 * with the existing per-email lockout, without needing every call site to
 * thread an HttpServletRequest through.
 */
public final class ClientIpUtils {

    private ClientIpUtils() {
    }

    /**
     * Best-effort originating client IP. api-gateway (Spring Cloud
     * Gateway) sets X-Forwarded-For by default, so the first entry there
     * is the real client — falls back to the direct socket address if
     * that header is absent (e.g. a request that somehow reached this
     * service without going through the gateway).
     */
    public static String getClientIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return "unknown";
        }
        HttpServletRequest request = servletAttrs.getRequest();

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
