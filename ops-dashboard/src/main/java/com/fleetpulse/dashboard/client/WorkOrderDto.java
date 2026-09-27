package com.fleetpulse.dashboard.client;

public record WorkOrderDto(
        String id,
        String vehicleId,
        String description,
        String status,
        String createdAt,
        String completedAt
) {
}
