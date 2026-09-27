package com.fleetpulse.maintenance.workorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {

    List<WorkOrder> findTop50ByOrderByCreatedAtDesc();

    List<WorkOrder> findByVehicleId(String vehicleId);
}
