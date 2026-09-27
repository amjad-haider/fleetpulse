package com.fleetpulse.maintenance.api;

import com.fleetpulse.maintenance.workorder.WorkOrder;
import com.fleetpulse.maintenance.workorder.WorkOrderStatus;

import java.time.Instant;
import java.util.UUID;

public record WorkOrderResponse(
        UUID id,
        String vehicleId,
        String description,
        WorkOrderStatus status,
        Instant createdAt,
        Instant completedAt
) {
    static WorkOrderResponse from(WorkOrder workOrder) {
        return new WorkOrderResponse(
                workOrder.getId(),
                workOrder.getVehicleId(),
                workOrder.getDescription(),
                workOrder.getStatus(),
                workOrder.getCreatedAt(),
                workOrder.getCompletedAt()
        );
    }
}
