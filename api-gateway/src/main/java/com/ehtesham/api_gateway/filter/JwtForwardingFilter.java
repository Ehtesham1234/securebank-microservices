package com.ehtesham.api_gateway.filter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/*
 * Simplified from the original design (extract JWT claims, rebuild
 * X-User-* headers, HMAC-sign them) to just: verify the token is valid,
 * then let it pass through UNCHANGED. Every downstream service now
 * verifies the same JWT itself (using the public key — see
 * GatewayAuthFilter in each service), so there's nothing left for the
 * gateway to extract-and-forward. This still verifies here too, purely
 * so a garbage/expired token gets a fast, clean 401 at the edge instead
 * of failing one hop later.
 */
@Component
public class JwtForwardingFilter implements GlobalFilter, Ordered {

    private static final Logger log =
            LoggerFactory.getLogger(JwtForwardingFilter.class);

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
        log.info("JWT Gateway Filter initialized (RS256, verify-only).");
    }

    /**
     * Public endpoints that do not require authentication.
     */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/email/send-otp",
            "/api/v1/auth/email/verify",
            "/actuator/health",
            "/swagger-ui",
            "/v3/api-docs",
            // Browsers can't set an Authorization header on a WebSocket
            // handshake, so this one authenticates itself downstream via a
            // ?token=<jwt> query param instead (see
            // WsAuthHandshakeInterceptor in securebank-api). Letting it
            // through here just means "no Authorization header required at
            // the gateway" — it is NOT unauthenticated end-to-end.
            "/ws"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             GatewayFilterChain chain) {

        String path = exchange.getRequest()
                .getURI()
                .getPath();

        boolean isPublic =
                PUBLIC_PATHS.stream().anyMatch(path::startsWith);

        if (isPublic) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token);

            // Valid — forward the request exactly as received.
            // Authorization header passes through unchanged; every
            // downstream service verifies it again independently.
            return chain.filter(exchange);

        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT for path {}", path);
            return unauthorized(exchange, "Token has expired");

        } catch (JwtException e) {
            log.warn("Invalid JWT for path {}", path);
            return unauthorized(exchange, "Invalid token");

        } catch (Exception e) {
            log.error("JWT processing failed", e);
            return unauthorized(exchange, "Authentication failed");
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange,
                                    String message) {

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");

        String body = String.format("""
                {
                  "status":401,
                  "error":"UNAUTHORIZED",
                  "message":"%s",
                  "success":false
                }
                """, message);

        DataBuffer buffer =
                response.bufferFactory()
                        .wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
