package com.ehtesham.api_gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/*
 * Bug fix: ClientIpUtils.getClientIp() (used for the registration
 * rate-limiter and login-lockout IP correlation in securebank-api)
 * trusts the first entry of an inbound X-Forwarded-For header
 * unconditionally. As deployed by this repo's own compose files,
 * api-gateway IS the internet-facing edge — the only microservice with
 * a published port, with no reverse proxy/LB in front of it — so
 * without this filter, a client could set their own X-Forwarded-For and
 * have every downstream service believe whatever IP they claimed,
 * bypassing both of those protections entirely.
 *
 * This runs before JwtForwardingFilter (lower order = higher
 * precedence) and unconditionally overwrites X-Forwarded-For with the
 * real TCP peer address for EVERY request — including public ones like
 * /api/v1/auth/register, which is exactly the endpoint this needed to
 * protect. Combined with forwarded-headers-strategy=none (see
 * application.properties), the gateway itself also won't be fooled by
 * an inbound Forwarded header when resolving the exchange's own view of
 * the request.
 *
 * If a real reverse proxy/LB is ever placed in front of api-gateway in
 * production, this needs to change — see the note in
 * application.properties.
 */
@Component
public class ClientIpOverwriteFilter implements GlobalFilter, Ordered {

    private static final Logger log =
            LoggerFactory.getLogger(ClientIpOverwriteFilter.class);

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             GatewayFilterChain chain) {

        InetSocketAddress remoteAddress = exchange.getRequest()
                .getRemoteAddress();

        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            // Shouldn't happen for a real inbound connection, but don't
            // block the request over it — just leave whatever header
            // was (or wasn't) already there.
            log.warn("Could not resolve remote address for inbound " +
                    "request to {}", exchange.getRequest().getURI().getPath());
            return chain.filter(exchange);
        }

        String realClientIp = remoteAddress.getAddress().getHostAddress();

        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    // Discard whatever the client sent — this is the
                    // one point in the whole system authoritative
                    // enough to set this header, since it's derived
                    // from the actual TCP connection, not from
                    // anything the client could have written itself.
                    headers.remove(X_FORWARDED_FOR);
                    headers.add(X_FORWARDED_FOR, realClientIp);
                })
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        // Must run before JwtForwardingFilter (order -1) so the header
        // is already trustworthy by the time anything downstream reads
        // it, and so it applies to public paths too (register/login).
        return -2;
    }
}
