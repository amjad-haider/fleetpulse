package com.fleetpulse.dashboard.client;

public record HealthScoreDto(
        String id,
        String vehicleId,
        double riskScore,
        String decision,
        String scoredAt
) {
}
