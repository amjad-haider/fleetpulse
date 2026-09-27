package com.fleetpulse.maintenance.api;

import com.fleetpulse.maintenance.workorder.WorkOrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateWorkOrderStatusRequest(
        @NotNull WorkOrderStatus status
) {
}
