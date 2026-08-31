package com.fleetpulse.alert.api;

import com.fleetpulse.alert.ingest.AlertSeverity;
import com.fleetpulse.alert.ingest.FleetAlert;

import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        String vehicleId,
        double riskScore,
        AlertSeverity severity,
        Instant raisedAt,
        boolean notificationSent
) {
    static AlertResponse from(FleetAlert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getVehicleId(),
                alert.getRiskScore(),
                alert.getSeverity(),
                alert.getRaisedAt(),
                alert.isNotificationSent()
        );
    }
}
