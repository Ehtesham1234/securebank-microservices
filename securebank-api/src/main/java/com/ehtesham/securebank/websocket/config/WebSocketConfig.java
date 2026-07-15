package com.ehtesham.securebank.websocket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig
        implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry) {

        // prefix for messages FROM server TO client
        // client subscribes to /topic/balance/{userId}
        registry.enableSimpleBroker("/topic");

        // prefix for messages FROM client TO server
        // (we don't use this much — mainly server pushes)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(
            StompEndpointRegistry registry) {

        // the URL clients connect to initially
        // SockJS provides fallback for browsers that don't
        // support WebSocket natively
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}