package com.fleetpulse.health.api;

import com.fleetpulse.health.ingest.HealthScoreRecord;
import com.fleetpulse.proto.health.v1.HealthDecision;

import java.time.Instant;
import java.util.UUID;

public record HealthScoreResponse(
        UUID id,
        String vehicleId,
        double riskScore,
        HealthDecision decision,
        Instant scoredAt
) {
    static HealthScoreResponse from(HealthScoreRecord record) {
        return new HealthScoreResponse(
                record.getId(),
                record.getVehicleId(),
                record.getRiskScore(),
                record.getDecision(),
                record.getScoredAt()
        );
    }
}
