package com.fleetpulse.fleet.vehicle;

import java.util.UUID;

public class VehicleNotFoundException extends RuntimeException {

    public VehicleNotFoundException(UUID id) {
        super("vehicle not found: " + id);
    }
}
