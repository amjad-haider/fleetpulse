package com.fleetpulse.fleet.vehicle;

import java.time.Instant;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        String vin,
        String make,
        String model,
        int year,
        String licensePlate,
        VehicleStatus status,
        double odometerKm,
        Instant createdAt
) {
    static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getVin(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getYear(),
                vehicle.getLicensePlate(),
                vehicle.getStatus(),
                vehicle.getOdometerKm(),
                vehicle.getCreatedAt()
        );
    }
}
