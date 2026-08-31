package com.fleetpulse.telemetry.ingest;

import java.time.Instant;
import java.util.List;

/**
 * What actually goes on the Kafka topic. Deliberately not the raw protobuf
 * message: consumers of this topic (health-engine, others later) shouldn't
 * need to depend on the gRPC contract just to read an event.
 */
public record TelemetryEvent(
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
    static TelemetryEvent from(TelemetryReading reading) {
        return new TelemetryEvent(
                reading.getVehicleId(),
                reading.getRecordedAt(),
                reading.getEngineTempC(),
                reading.getVibrationMmS(),
                reading.getRpm(),
                reading.getFuelLevelPct(),
                reading.getBrakeWearPct(),
                reading.getOdometerKm(),
                reading.getFaultCodes()
        );
    }
}
