package com.fleetpulse.maintenance.workorder;

import java.util.UUID;

public class WorkOrderNotFoundException extends RuntimeException {

    public WorkOrderNotFoundException(UUID id) {
        super("no work order with id " + id);
    }
}
