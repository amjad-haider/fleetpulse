package com.fleetpulse.telemetry.ingest;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "telemetry_readings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelemetryReading {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private String vehicleId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "engine_temp_c", nullable = false)
    private double engineTempC;

    @Column(name = "vibration_mm_s", nullable = false)
    private double vibrationMmS;

    @Column(nullable = false)
    private int rpm;

    @Column(name = "fuel_level_pct", nullable = false)
    private double fuelLevelPct;

    @Column(name = "brake_wear_pct", nullable = false)
    private double brakeWearPct;

    @Column(name = "odometer_km", nullable = false)
    private double odometerKm;

    @ElementCollection
    @CollectionTable(name = "telemetry_reading_fault_codes", joinColumns = @JoinColumn(name = "reading_id"))
    @Column(name = "fault_code")
    @Builder.Default
    private List<String> faultCodes = new ArrayList<>();

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @PrePersist
    void onCreate() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }
}
