package com.fleetpulse.health.ingest;

import java.time.Instant;
import java.util.List;

/**
 * Mirrors the JSON shape telemetry-service publishes to telemetry.readings.
 * Deliberately a separate type rather than a shared library class — this
 * service shouldn't need a compile-time dependency on telemetry-service just
 * to read its events off Kafka.
 */
public record TelemetryReadingEvent(
        String vehicleId,
        Instant recordedAt,
        double engineTempC,
        double vibrationMmS,
        int rpm,
        double fuelLevelPct,
        double brakeWearPct,
        double odometerKm,
        List<String> faultCodes
) {
}
