package com.fleetpulse.maintenance.workorder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;

    public WorkOrderService(WorkOrderRepository workOrderRepository) {
        this.workOrderRepository = workOrderRepository;
    }

    @Transactional
    public WorkOrder create(String vehicleId, String description) {
        WorkOrder workOrder = WorkOrder.builder()
                .vehicleId(vehicleId)
                .description(description)
                .status(WorkOrderStatus.OPEN)
                .createdAt(Instant.now())
                .build();
        return workOrderRepository.save(workOrder);
    }

    @Transactional
    public WorkOrder updateStatus(UUID id, WorkOrderStatus status) {
        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new WorkOrderNotFoundException(id));
        workOrder.setStatus(status);
        workOrder.setCompletedAt(status == WorkOrderStatus.COMPLETED ? Instant.now() : null);
        return workOrderRepository.save(workOrder);
    }

    @Transactional(readOnly = true)
    public List<WorkOrder> recent() {
        return workOrderRepository.findTop50ByOrderByCreatedAtDesc();
    }
}
