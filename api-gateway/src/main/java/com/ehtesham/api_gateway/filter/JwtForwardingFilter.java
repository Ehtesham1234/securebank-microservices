package com.ehtesham.api_gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtForwardingFilter implements GlobalFilter, Ordered {

    private static final Logger log =
            LoggerFactory.getLogger(JwtForwardingFilter.class);

    private static final String HEADER_EMAIL = "X-User-Email";
    private static final String HEADER_ID = "X-User-Id";
    private static final String HEADER_ROLE = "X-User-Role";

    @Value("${jwt.secret}")
    private String jwtSecret;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {

        if (jwtSecret == null ||
                jwtSecret.isBlank() ||
                "your-secret-key-here".equals(jwtSecret)) {

            throw new IllegalStateException(
                    "JWT secret is not configured. Please configure 'jwt.secret'.");
        }

        secretKey = Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8));

        log.info("JWT Gateway Filter initialized.");
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
            "/v3/api-docs"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             GatewayFilterChain chain) {

        String path = exchange.getRequest()
                .getURI()
                .getPath();

        // Skip authentication for public endpoints
        boolean isPublic =
                PUBLIC_PATHS.stream().anyMatch(path::startsWith);

        if (isPublic) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {

            return unauthorized(
                    exchange,
                    "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        try {

            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String email = claims.getSubject();
            String userId = claims.get("userId", String.class);
            String role = claims.get("role", String.class);

            ServerHttpRequest modifiedRequest =
                    exchange.getRequest()
                            .mutate()
                            .headers(headers -> {

                                headers.set(
                                        HEADER_EMAIL,
                                        email != null ? email : "");

                                headers.set(
                                        HEADER_ID,
                                        userId != null ? userId : "");

                                headers.set(
                                        HEADER_ROLE,
                                        role != null ? role : "");

                            })
                            .build();

            log.debug(
                    "JWT validated successfully. user={}, role={}, path={}",
                    email,
                    role,
                    path);

            return chain.filter(
                    exchange.mutate()
                            .request(modifiedRequest)
                            .build());

        } catch (ExpiredJwtException e) {

            log.warn("Expired JWT for path {}", path);

            return unauthorized(
                    exchange,
                    "Token has expired");

        } catch (JwtException e) {

            log.warn("Invalid JWT for path {}", path);

            return unauthorized(
                    exchange,
                    "Invalid token");

        } catch (Exception e) {

            log.error("JWT processing failed", e);

            return unauthorized(
                    exchange,
                    "Authentication failed");
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange,
                                    String message) {

        ServerHttpResponse response = exchange.getResponse();

        response.setStatusCode(HttpStatus.UNAUTHORIZED);

        response.getHeaders().set(
                HttpHeaders.CONTENT_TYPE,
                "application/json");

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

        // Run before routing filters
        return -1;
    }
}