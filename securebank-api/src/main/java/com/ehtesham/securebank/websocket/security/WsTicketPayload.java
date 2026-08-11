package com.ehtesham.securebank.websocket.security;

/**
 * L5 fix: what a WS ticket resolves to once consumed. Deliberately
 * minimal — just enough to bind the STOMP session's Principal
 * (WsPrincipalHandshakeHandler), nothing else is carried through the
 * handshake.
 */
public record WsTicketPayload(Long userId, String email) {
}
