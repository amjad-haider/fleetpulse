package com.fleetpulse.health.ingest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HealthScoreRepository extends JpaRepository<HealthScoreRecord, UUID> {

    List<HealthScoreRecord> findTop50ByOrderByScoredAtDesc();
}
