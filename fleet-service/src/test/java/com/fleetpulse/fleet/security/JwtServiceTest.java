package com.fleetpulse.fleet.security;

import com.fleetpulse.fleet.auth.User;
import com.fleetpulse.fleet.auth.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-jwt-secret-needs-to-be-at-least-32-bytes-long";

    private final JwtService jwtService = new JwtService(SECRET, 60_000);

    private User someUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("manager@fleetpulse.dev")
                .passwordHash("irrelevant-for-this-test")
                .fullName("Test Manager")
                .role(UserRole.FLEET_MANAGER)
                .build();
    }

    @Test
    void generatedTokenRoundTripsSubjectAndRole() {
        String token = jwtService.generateToken(someUser());

        Claims claims = jwtService.parse(token);

        assertThat(claims.getSubject()).isEqualTo("manager@fleetpulse.dev");
        assertThat(claims.get("role", String.class)).isEqualTo("FLEET_MANAGER");
        assertThat(jwtService.expiryOf(token)).isAfter(Instant.now());
    }

    @Test
    void parseRejectsTokenSignedWithADifferentSecret() {
        JwtService otherService = new JwtService("a-completely-different-signing-secret-of-decent-length", 60_000);
        String token = otherService.generateToken(someUser());

        assertThatThrownBy(() -> jwtService.parse(token))
                .isInstanceOf(SignatureException.class);
    }
}
