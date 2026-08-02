package com.ehtesham.securebank.websocket.security;

import com.ehtesham.securebank.security.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * Runs BEFORE the WebSocket/SockJS upgrade completes.
 *
 * The browser WebSocket API (and SockJS) cannot set an Authorization header
 * on the handshake request, so the access token is passed as a query param
 * instead: ws://host/ws?token=<accessToken>
 *
 * If the token is missing or invalid, the handshake is rejected outright
 * (401) — no connection is established at all. If valid, the userId is
 * stashed in the session attributes so WsPrincipalHandshakeHandler can bind
 * it as the STOMP session's Principal.
 */
@Component
public class WsAuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log =
            LoggerFactory.getLogger(WsAuthHandshakeInterceptor.class);

    private final JwtService jwtService;

    public WsAuthHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        List<String> tokenParams = UriComponentsBuilder
                .fromUri(request.getURI())
                .build()
                .getQueryParams()
                .get("token");

        String token = (tokenParams == null || tokenParams.isEmpty())
                ? null
                : tokenParams.get(0);

        if (token == null || token.isBlank() || !jwtService.isTokenValid(token)) {
            log.warn("Rejected WebSocket handshake — missing or invalid token");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String userId = jwtService.extractClaim(
                token, claims -> claims.get("userId", String.class));

        if (userId == null || userId.isBlank()) {
            log.warn("Rejected WebSocket handshake — token has no userId claim");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put("userId", userId);
        attributes.put("email", jwtService.extractUsername(token));

        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }
}
