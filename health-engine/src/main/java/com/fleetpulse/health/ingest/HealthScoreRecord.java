package com.fleetpulse.health.ingest;

import com.fleetpulse.proto.health.v1.HealthDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "health_scores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthScoreRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private String vehicleId;

    @Column(name = "risk_score", nullable = false)
    private double riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HealthDecision decision;

    @Column(name = "scored_at", nullable = false, updatable = false)
    private Instant scoredAt;

    @PrePersist
    void onCreate() {
        if (scoredAt == null) {
            scoredAt = Instant.now();
        }
    }
}
