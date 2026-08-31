package com.fleetpulse.dashboard.client;

public record VehicleDto(
        String id,
        String vin,
        String make,
        String model,
        int year,
        String licensePlate,
        String status,
        double odometerKm
) {
}
