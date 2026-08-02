package com.ehtesham.securebank.websocket.security;

import java.security.Principal;

/**
 * Binds a STOMP/WebSocket session to the authenticated user's id.
 * getName() returns the userId (as a String) — this is what Spring uses
 * internally as the key for convertAndSendToUser(...) / "/user/**" routing,
 * so it must exactly match the userId string passed to convertAndSendToUser.
 */
public class StompPrincipal implements Principal {

    private final String userId;

    public StompPrincipal(String userId) {
        this.userId = userId;
    }

    @Override
    public String getName() {
        return userId;
    }
}
