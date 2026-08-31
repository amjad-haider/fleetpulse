package com.fleetpulse.health.ingest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HealthScoreRepository extends JpaRepository<HealthScoreRecord, UUID> {
}
