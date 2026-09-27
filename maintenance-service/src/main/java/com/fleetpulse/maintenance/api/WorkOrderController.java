package com.fleetpulse.maintenance.api;

import com.fleetpulse.maintenance.workorder.ReconciliationEntry;
import com.fleetpulse.maintenance.workorder.ReconciliationService;
import com.fleetpulse.maintenance.workorder.WorkOrder;
import com.fleetpulse.maintenance.workorder.WorkOrderNotFoundException;
import com.fleetpulse.maintenance.workorder.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;
    private final ReconciliationService reconciliationService;

    public WorkOrderController(WorkOrderService workOrderService, ReconciliationService reconciliationService) {
        this.workOrderService = workOrderService;
        this.reconciliationService = reconciliationService;
    }

    @PostMapping
    public ResponseEntity<WorkOrderResponse> create(@Valid @RequestBody CreateWorkOrderRequest request) {
        WorkOrder workOrder = workOrderService.create(request.vehicleId(), request.description());
        WorkOrderResponse response = WorkOrderResponse.from(workOrder);
        return ResponseEntity.created(URI.create("/api/v1/work-orders/" + response.id())).body(response);
    }

    @PatchMapping("/{id}/status")
    public WorkOrderResponse updateStatus(@PathVariable("id") UUID id, @Valid @RequestBody UpdateWorkOrderStatusRequest request) {
        return WorkOrderResponse.from(workOrderService.updateStatus(id, request.status()));
    }

    @GetMapping("/recent")
    public List<WorkOrderResponse> recent() {
        return workOrderService.recent().stream().map(WorkOrderResponse::from).toList();
    }

    @GetMapping("/reconciliation")
    public List<ReconciliationEntry> reconciliation() {
        return reconciliationService.reconcileFleet();
    }

    @ExceptionHandler(WorkOrderNotFoundException.class)
    public ResponseEntity<String> handleNotFound(WorkOrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }
}
