package com.ehtesham.securebank.websocket.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * L5 fix: the WebSocket handshake used to be authenticated with the
 * user's real access token passed as a query param
 * (ws://host/ws?token=<accessToken>) — a well-known, browser-forced
 * tradeoff (custom headers aren't settable on a WS/SockJS handshake), but
 * query params can end up in access logs, proxy logs, and browser
 * history. That's a real exposure for a token that's also valid against
 * every REST endpoint for up to jwt.expiration (15 min by default).
 * <p>
 * This replaces it with a short-lived, single-use ticket:
 * <ol>
 *   <li>The client calls {@code POST /api/v1/ws/ticket} over a normal,
 *   authenticated HTTPS request (real {@code Authorization} header, not a
 *   query param) to get a ticket.</li>
 *   <li>The client opens the WS/SockJS connection with
 *   {@code ?ticket=<ticket>} instead of {@code ?token=<jwt>}.</li>
 *   <li>{@link WsAuthHandshakeInterceptor} consumes the ticket — looks it
 *   up, and atomically removes it so it can never be replayed, even by
 *   the legitimate client itself.</li>
 * </ol>
 * If a ticket does leak into a log somewhere, it's useless within
 * {@link #TICKET_TTL} and useless after the first (successful or
 * failed) connection attempt — unlike the raw access token it replaces,
 * it's not valid against anything else and can't be reused.
 */
@Service
public class WsTicketService {

    private static final Duration TICKET_TTL = Duration.ofSeconds(30);
    private static final int TICKET_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    // Short TTL as the primary defense; maximumSize is just a backstop
    // against a burst outpacing expiry, same reasoning as the M3 fix on
    // RateLimiterService.
    private final Cache<String, WsTicketPayload> tickets = Caffeine.newBuilder()
            .expireAfterWrite(TICKET_TTL)
            .maximumSize(50_000)
            .build();

    public String issueTicket(Long userId, String email) {
        byte[] raw = new byte[TICKET_BYTES];
        secureRandom.nextBytes(raw);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        tickets.put(ticket, new WsTicketPayload(userId, email));
        return ticket;
    }

    public long ttlSeconds() {
        return TICKET_TTL.getSeconds();
    }

    /**
     * Looks up and immediately invalidates the ticket in one step, so a
     * ticket can never be consumed twice — not by an attacker who
     * observed it in a log, and not by the legitimate client retrying a
     * failed handshake with the same value.
     */
    public WsTicketPayload consumeTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }

        AtomicReference<WsTicketPayload> result = new AtomicReference<>();
        tickets.asMap().computeIfPresent(ticket, (k, payload) -> {
            result.set(payload);
            return null; // removes the entry
        });
        return result.get();
    }
}
