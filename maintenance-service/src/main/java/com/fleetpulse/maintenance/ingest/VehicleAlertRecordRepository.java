package com.fleetpulse.maintenance.ingest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VehicleAlertRecordRepository extends JpaRepository<VehicleAlertRecord, UUID> {

    List<VehicleAlertRecord> findByVehicleId(String vehicleId);

    @Query("select distinct r.vehicleId from VehicleAlertRecord r where r.severity = :severity")
    List<String> findDistinctVehicleIdsBySeverity(@Param("severity") AlertSeverity severity);
}
