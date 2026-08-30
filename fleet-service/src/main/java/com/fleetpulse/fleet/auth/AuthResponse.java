package com.fleetpulse.fleet.auth;

import java.time.Instant;

public record AuthResponse(
        String token,
        Instant expiresAt,
        UserRole role
) {
}
