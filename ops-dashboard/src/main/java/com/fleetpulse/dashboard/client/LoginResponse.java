package com.fleetpulse.dashboard.client;

public record LoginResponse(String token, String expiresAt, String role) {
}
