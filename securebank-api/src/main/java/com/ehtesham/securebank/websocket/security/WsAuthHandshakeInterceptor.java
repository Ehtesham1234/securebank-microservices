package com.ehtesham.securebank.websocket.security;

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
 * L5 fix: this used to pass the user's real access token as a query param
 * (ws://host/ws?token=<accessToken>) — the browser WebSocket/SockJS API
 * can't set an Authorization header on the handshake, so a query param was
 * unavoidable, but that meant a token valid against every REST endpoint for
 * up to jwt.expiration (15 min) could end up in access logs, proxy logs, or
 * browser history.
 *
 * Now the query param carries a short-lived, single-use ticket instead
 * (ws://host/ws?ticket=<ticket>), minted by WsTicketController /
 * consumed by WsTicketService. A leaked ticket is useless within 30
 * seconds, and useless after the first handshake attempt either way — it
 * isn't valid for anything else and can't be replayed.
 *
 * If the ticket is missing, unknown, or already used, the handshake is
 * rejected outright (401) — no connection is established at all. If
 * valid, the userId/email it resolves to are stashed in the session
 * attributes so WsPrincipalHandshakeHandler can bind them as the STOMP
 * session's Principal.
 */
@Component
public class WsAuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log =
            LoggerFactory.getLogger(WsAuthHandshakeInterceptor.class);

    private final WsTicketService wsTicketService;

    public WsAuthHandshakeInterceptor(WsTicketService wsTicketService) {
        this.wsTicketService = wsTicketService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        List<String> ticketParams = UriComponentsBuilder
                .fromUri(request.getURI())
                .build()
                .getQueryParams()
                .get("ticket");

        String ticket = (ticketParams == null || ticketParams.isEmpty())
                ? null
                : ticketParams.get(0);

        WsTicketPayload payload = wsTicketService.consumeTicket(ticket);

        if (payload == null || payload.userId() == null) {
            log.warn("Rejected WebSocket handshake — missing, unknown, " +
                    "or expired ticket");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put("userId", payload.userId().toString());
        attributes.put("email", payload.email());

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
