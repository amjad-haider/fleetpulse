package com.fleetpulse.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtValidatorTest {

    private static final String SECRET = "gateway-test-jwt-secret-needs-to-be-at-least-32-bytes-long";

    private final JwtValidator validator = new JwtValidator(SECRET);

    private String tokenSignedWith(String secret, String subject, long expiresInMs) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claim("role", "FLEET_MANAGER")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiresInMs))
                .signWith(key)
                .compact();
    }

    @Test
    void acceptsATokenSignedWithTheMatchingSecret() {
        String token = tokenSignedWith(SECRET, "ops@fleetpulse.dev", 60_000);

        Claims claims = validator.validate(token);

        assertThat(claims.getSubject()).isEqualTo("ops@fleetpulse.dev");
        assertThat(claims.get("role", String.class)).isEqualTo("FLEET_MANAGER");
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        String token = tokenSignedWith("a-completely-different-secret-of-decent-length-here", "ops@fleetpulse.dev", 60_000);

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        String token = tokenSignedWith(SECRET, "ops@fleetpulse.dev", -1_000);

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }
}
