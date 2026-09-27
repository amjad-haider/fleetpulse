package com.fleetpulse.maintenance.api;

import jakarta.validation.constraints.NotBlank;

public record CreateWorkOrderRequest(
        @NotBlank String vehicleId,
        @NotBlank String description
) {
}
