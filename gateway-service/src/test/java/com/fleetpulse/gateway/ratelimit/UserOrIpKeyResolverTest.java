package com.fleetpulse.gateway.ratelimit;

import com.fleetpulse.gateway.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

class UserOrIpKeyResolverTest {

    private final UserOrIpKeyResolver resolver = new UserOrIpKeyResolver();

    @Test
    void keysByJwtSubjectWhenTheRequestIsAuthenticated() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/vehicles"));
        exchange.getAttributes().put(JwtAuthenticationFilter.SUBJECT_ATTRIBUTE, "ops@fleetpulse.dev");

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("ops@fleetpulse.dev")
                .verifyComplete();
    }

    @Test
    void fallsBackToTheCallersAddressForUnauthenticatedRequests() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login")
                        .remoteAddress(new InetSocketAddress("203.0.113.7", 54321)));

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("203.0.113.7")
                .verifyComplete();
    }

    @Test
    void fallsBackToUnknownWhenThereIsNoAddressAtAll() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/vehicles"));

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("unknown")
                .verifyComplete();
    }
}
