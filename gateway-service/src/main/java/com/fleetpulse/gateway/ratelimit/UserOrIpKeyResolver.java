package com.fleetpulse.gateway.ratelimit;

import com.fleetpulse.gateway.security.JwtAuthenticationFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Picks what bucket a request counts against for the RequestRateLimiter
 * filter. JwtAuthenticationFilter runs first and, for an authenticated
 * request, leaves the token's subject on the exchange — that's the real
 * identity to limit by, so two people behind the same office NAT don't
 * throttle each other. /api/v1/auth/** requests never carry a token (that's
 * where one comes from), so those fall back to the caller's address, which
 * is exactly what you want for slowing down login/registration brute-forcing.
 */
@Component
public class UserOrIpKeyResolver implements KeyResolver {

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String subject = exchange.getAttribute(JwtAuthenticationFilter.SUBJECT_ATTRIBUTE);
        if (subject != null) {
            return Mono.just(subject);
        }

        var remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return Mono.just("unknown");
        }
        return Mono.just(remoteAddress.getAddress().getHostAddress());
    }
}
