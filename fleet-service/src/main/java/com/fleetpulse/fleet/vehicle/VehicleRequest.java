package com.fleetpulse.fleet.vehicle;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VehicleRequest(
        @NotBlank @Size(min = 17, max = 17) String vin,
        @NotBlank String make,
        @NotBlank String model,
        @Min(1980) @Max(2100) int year,
        @NotBlank String licensePlate
) {
}
