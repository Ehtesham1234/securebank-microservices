package com.ehtesham.securebank.websocket.config;

import com.ehtesham.securebank.websocket.security.WsAuthHandshakeInterceptor;
import com.ehtesham.securebank.websocket.security.WsPrincipalHandshakeHandler;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig
        implements WebSocketMessageBrokerConfigurer {

    // Keep this in sync with the CORS allow-lists in
    // api-gateway/ApiGatewayApplication.java and
    // securebank-api/config/CorsConfig.java.
    private static final List<String> ALLOWED_ORIGINS = List.of(
            "http://localhost:3000",
            "http://localhost:4200",
            "http://localhost:5173",
            "http://localhost:5500",
            "http://127.0.0.1:5500"
    );

    private final WsAuthHandshakeInterceptor wsAuthHandshakeInterceptor;
    private final WsPrincipalHandshakeHandler wsPrincipalHandshakeHandler;

    public WebSocketConfig(
            WsAuthHandshakeInterceptor wsAuthHandshakeInterceptor,
            WsPrincipalHandshakeHandler wsPrincipalHandshakeHandler) {
        this.wsAuthHandshakeInterceptor = wsAuthHandshakeInterceptor;
        this.wsPrincipalHandshakeHandler = wsPrincipalHandshakeHandler;
    }

    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry) {

        // "/queue" is required for convertAndSendToUser(...) — Spring
        // rewrites "/user/queue/balance" to a per-session destination
        // under "/queue" internally, so the broker must carry that prefix.
        // "/topic" is kept in case anything else needs broadcast-style
        // messaging later; nothing currently uses it.
        registry.enableSimpleBroker("/topic", "/queue");

        // prefix for messages FROM client TO server
        // (we don't use this much — mainly server pushes)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(
            StompEndpointRegistry registry) {

        // The URL clients connect to initially. SockJS provides a fallback
        // for browsers that don't support WebSocket natively.
        //
        // WsAuthHandshakeInterceptor validates a JWT (passed as a
        // ?token=... query param — browsers can't set custom headers on a
        // WebSocket handshake) before the upgrade is allowed, and
        // WsPrincipalHandshakeHandler binds the resulting userId as this
        // session's Principal so convertAndSendToUser(...) can target it.
        registry.addEndpoint("/ws")
                .setAllowedOrigins(ALLOWED_ORIGINS.toArray(new String[0]))
                .addInterceptors(wsAuthHandshakeInterceptor)
                .setHandshakeHandler(wsPrincipalHandshakeHandler)
                .withSockJS();
    }
}
