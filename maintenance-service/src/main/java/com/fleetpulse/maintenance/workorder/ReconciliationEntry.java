package com.fleetpulse.maintenance.workorder;

import java.time.Instant;

public record ReconciliationEntry(
        String vehicleId,
        double riskScore,
        Instant alertRaisedAt,
        ReconciliationStatus status
) {
}
