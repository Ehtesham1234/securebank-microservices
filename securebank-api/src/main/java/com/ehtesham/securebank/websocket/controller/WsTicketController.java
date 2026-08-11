package com.ehtesham.securebank.websocket.controller;

import com.ehtesham.securebank.common.exception.RateLimitExceededException;
import com.ehtesham.securebank.common.response.ApiResponse;
import com.ehtesham.securebank.security.ratelimit.RateLimiterService;
import com.ehtesham.securebank.security.service.CustomUserPrincipal;
import com.ehtesham.securebank.websocket.dto.WsTicketResponse;
import com.ehtesham.securebank.websocket.security.WsTicketService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * L5 fix: issues short-lived, single-use tickets for WebSocket
 * authentication — see {@link WsTicketService} for the full rationale.
 * This endpoint itself is reached the normal way, over a real
 * authenticated HTTPS request with a proper Authorization header, so
 * nothing new is exposed via a query param here.
 */
@RestController
@RequestMapping("/api/v1/ws")
@Tag(name = "WebSocket", description = "WebSocket handshake authentication")
public class WsTicketController {

    // Generous enough for normal reconnect/tab-open behavior, tight
    // enough that it's not a useful way to mint a pile of valid tickets.
    private static final int MAX_TICKETS_PER_WINDOW = 20;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final WsTicketService wsTicketService;
    private final RateLimiterService rateLimiterService;

    public WsTicketController(
            WsTicketService wsTicketService,
            RateLimiterService rateLimiterService) {
        this.wsTicketService = wsTicketService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/ticket")
    public ResponseEntity<ApiResponse<WsTicketResponse>> issueTicket(
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        boolean allowed = rateLimiterService.tryConsume(
                "ws-ticket:" + principal.getUserId(),
                MAX_TICKETS_PER_WINDOW,
                WINDOW);

        if (!allowed) {
            throw new RateLimitExceededException(
                    "Too many ticket requests. Please try again shortly.");
        }

        String ticket = wsTicketService.issueTicket(
                principal.getUserId(), principal.getUsername());

        return ResponseEntity.ok(ApiResponse.success(
                "WebSocket ticket issued",
                new WsTicketResponse(ticket, wsTicketService.ttlSeconds())));
    }
}
