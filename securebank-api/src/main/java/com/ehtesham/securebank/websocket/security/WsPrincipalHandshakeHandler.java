package com.ehtesham.securebank.websocket.security;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Turns the userId that WsAuthHandshakeInterceptor validated and stashed in
 * the session attributes into the STOMP session's Principal. This is what
 * makes convertAndSendToUser(userId, ...) / "/user/**" destinations work —
 * without a Principal here, Spring has no authenticated identity to route
 * user-specific messages to.
 */
@Component
public class WsPrincipalHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        Object userId = attributes.get("userId");

        if (userId == null) {
            // Should never happen — WsAuthHandshakeInterceptor rejects the
            // handshake before it gets here if there's no valid userId.
            // Returning null would let an unauthenticated session through,
            // so fail loudly instead.
            throw new IllegalStateException(
                    "WebSocket handshake reached principal binding with no userId — " +
                            "WsAuthHandshakeInterceptor should have rejected this earlier.");
        }

        return new StompPrincipal(userId.toString());
    }
}
