package com.ehtesham.kyc_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Simplified further: "/api/v1/internal/**" no longer gets special
 * treatment at the filter level — see account-service's GatewayAuthFilter
 * for the full reasoning. kyc-service's status handling stays different
 * on purpose: PENDING_KYC users ARE allowed here (this is where they
 * complete KYC).
 */
@Component
public class GatewayAuthFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(GatewayAuthFilter.class);

    @Value("${jwt.public-key}")
    private String publicKeyBase64Pem;

    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        if (publicKeyBase64Pem == null || publicKeyBase64Pem.isBlank()) {
            throw new IllegalStateException(
                    "jwt.public-key is not configured.");
        }
        try {
            String pem = new String(
                    Base64.getDecoder().decode(publicKeyBase64Pem),
                    StandardCharsets.UTF_8);
            String cleaned = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(cleaned);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            this.publicKey = kf.generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load jwt.public-key — check it's a "
                            + "base64-encoded X.509 PEM public key.", e);
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (path.startsWith("/actuator/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, 401,
                    "Missing or invalid Authorization header.");
            return;
        }
        String token = authHeader.substring(7);

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            sendError(response, 401, "Token has expired.");
            return;
        } catch (JwtException e) {
            sendError(response, 401, "Invalid token.");
            return;
        }

        String userIdStr = claims.get("userId", String.class);
        String roleHeader = claims.get("role", String.class);
        String emailHeader = claims.getSubject();
        String statusHeader = claims.get("userStatus", String.class);

        try {
            Long userId = Long.parseLong(userIdStr);

            // kyc-service enforces status differently:
            // PENDING_KYC → ALLOWED (this is why they're here)
            // SUSPENDED / CLOSED → BLOCKED
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

            auth.setDetails(userId);

            SecurityContextHolder.getContext()
                    .setAuthentication(auth);

            HttpServletRequest wrapped = wrapWithUserHeaders(
                    request, userIdStr, roleHeader, emailHeader, statusHeader);

            try {
                filterChain.doFilter(wrapped, response);
            } finally {
                SecurityContextHolder.clearContext();
            }

        } catch (NumberFormatException e) {
            sendError(response, 401, "Invalid token claims.");
        }
    }

    private HttpServletRequest wrapWithUserHeaders(
            HttpServletRequest request, String userId, String role,
            String email, String status) {

        Map<String, String> extra = new HashMap<>();
        extra.put("X-User-Id", userId != null ? userId : "");
        extra.put("X-User-Role", role != null ? role : "");
        extra.put("X-User-Email", email != null ? email : "");
        extra.put("X-User-Status", status != null ? status : "");

        return new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if (extra.containsKey(name)) {
                    return extra.get(name);
                }
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if (extra.containsKey(name)) {
                    return Collections.enumeration(
                            Collections.singletonList(extra.get(name)));
                }
                return super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                java.util.Set<String> names = new java.util.HashSet<>(
                        Collections.list(super.getHeaderNames()));
                names.addAll(extra.keySet());
                return Collections.enumeration(names);
            }
        };
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
