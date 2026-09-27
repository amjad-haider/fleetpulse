package com.fleetpulse.maintenance.workorder;

import com.fleetpulse.maintenance.ingest.AlertSeverity;
import com.fleetpulse.maintenance.ingest.VehicleAlertRecordRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ReconciliationService {

    private final VehicleAlertRecordRepository alertRecordRepository;
    private final WorkOrderRepository workOrderRepository;
    private final MaintenanceReconciler reconciler;

    public ReconciliationService(
            VehicleAlertRecordRepository alertRecordRepository,
            WorkOrderRepository workOrderRepository,
            MaintenanceReconciler reconciler
    ) {
        this.alertRecordRepository = alertRecordRepository;
        this.workOrderRepository = workOrderRepository;
        this.reconciler = reconciler;
    }

    public List<ReconciliationEntry> reconcileFleet() {
        Instant now = Instant.now();
        return alertRecordRepository.findDistinctVehicleIdsBySeverity(AlertSeverity.SERVICE_NOW).stream()
                .flatMap(vehicleId -> reconciler.reconcile(
                        alertRecordRepository.findByVehicleId(vehicleId),
                        workOrderRepository.findByVehicleId(vehicleId),
                        now
                ).stream())
                .toList();
    }
}
