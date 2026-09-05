package com.fleetpulse.gateway.ratelimit;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real RequestRateLimiter filter against a real Redis
 * (Testcontainers) rather than trusting Spring Cloud Gateway's wiring by
 * inspection. The burst capacity is turned down to 2 for the test so it
 * doesn't take dozens of requests to prove the limiter actually engages.
 *
 * Both anonymous requests in this test class share one Redis-backed bucket
 * (they're all keyed by the same loopback IP), so the exhaustion test runs
 * first and deliberately - the independent-bucket test only ever needs that
 * shared bucket to already be spent, which holds however the first test left
 * it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("ratelimit-test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RateLimitingIntegrationTest {

    private static final String JWT_SECRET = "gateway-ratelimit-test-secret-needs-to-be-at-least-32-bytes";

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static DisposableServer downstream;

    @BeforeAll
    static void startDownstream() {
        // stands in for fleet-service: always answers 200 so every response
        // outside 429 proves the request actually got routed through
        downstream = HttpServer.create()
                .host("localhost")
                .port(0)
                .handle((request, response) -> response.status(HttpStatus.OK.value()).send())
                .bindNow();
    }

    @AfterAll
    static void stopDownstream() {
        downstream.disposeNow();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getFirstMappedPort().toString());
        registry.add("FLEET_SERVICE_URL", () -> "http://localhost:" + downstream.port());
        registry.add("security.jwt.secret", () -> JWT_SECRET);
    }

    @Autowired
    private WebTestClient webTestClient;

    private static String validToken(String subject) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(key)
                .compact();
    }

    @Test
    @Order(1)
    void requestsBeyondTheBurstCapacityAreRejectedWith429() {
        List<HttpStatusCode> statuses = IntStream.range(0, 5)
                .mapToObj(i -> webTestClient.post().uri("/api/v1/auth/login")
                        .exchange()
                        .returnResult(Void.class)
                        .getStatus())
                .toList();

        assertThat(statuses).contains(HttpStatus.OK);
        assertThat(statuses).contains(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @Order(2)
    void anAuthenticatedUsersBucketIsIndependentOfTheAnonymousIpBucket() {
        // the anonymous/IP bucket is already spent by the previous test (and
        // this repeats the burst just in case it isn't, since a real client
        // hammering /login is exactly the scenario this protects)
        IntStream.range(0, 3).forEach(i -> webTestClient.post().uri("/api/v1/auth/login").exchange());
        HttpStatusCode anonStatus = webTestClient.post().uri("/api/v1/auth/login")
                .exchange()
                .returnResult(Void.class)
                .getStatus();
        assertThat(anonStatus).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // a fresh authenticated identity, on a protected route, still gets
        // through - proves the key resolver is really keying by JWT subject
        // there, not falling back to the same exhausted IP bucket
        HttpStatusCode authedStatus = webTestClient.get().uri("/api/v1/vehicles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken("driver@fleetpulse.dev"))
                .exchange()
                .returnResult(Void.class)
                .getStatus();
        assertThat(authedStatus).isEqualTo(HttpStatus.OK);
    }
}
