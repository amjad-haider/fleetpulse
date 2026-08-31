package com.fleetpulse.health.ingest;

import com.fleetpulse.proto.health.v1.HealthDecision;

import java.time.Instant;

public record HealthAlert(
        String vehicleId,
        double riskScore,
        HealthDecision decision,
        Instant raisedAt
) {
}
