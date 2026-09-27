package com.fleetpulse.dashboard.client;

public record ReconciliationEntryDto(
        String vehicleId,
        double riskScore,
        String alertRaisedAt,
        String status
) {
}
