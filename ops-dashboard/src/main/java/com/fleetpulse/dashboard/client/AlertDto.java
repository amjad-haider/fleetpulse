package com.fleetpulse.dashboard.client;

public record AlertDto(
        String id,
        String vehicleId,
        double riskScore,
        String severity,
        String raisedAt,
        boolean notificationSent
) {
}
