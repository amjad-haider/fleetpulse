package com.fleetpulse.telemetry.ingest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TelemetryReadingRepository extends JpaRepository<TelemetryReading, UUID> {
}
