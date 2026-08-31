package com.fleetpulse.alert.ingest;

import java.time.Instant;

public record HealthAlertEvent(
        String vehicleId,
        double riskScore,
        AlertSeverity decision,
        Instant raisedAt
) {
}
