package com.fleetpulse.alert.ingest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FleetAlertRepository extends JpaRepository<FleetAlert, UUID> {

    List<FleetAlert> findByVehicleIdAndRaisedAtAfter(String vehicleId, Instant cutoff);

    List<FleetAlert> findTop50ByOrderByRaisedAtDesc();
}
