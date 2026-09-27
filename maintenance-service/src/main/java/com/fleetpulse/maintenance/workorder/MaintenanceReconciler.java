package com.fleetpulse.maintenance.workorder;

import com.fleetpulse.maintenance.ingest.AlertSeverity;
import com.fleetpulse.maintenance.ingest.VehicleAlertRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * The actual "reconciles predictions against actual maintenance" logic: for
 * every SERVICE_NOW alert a vehicle got, was it followed by a completed work
 * order within a reasonable response window? One completed work order can
 * cover several alerts raised shortly before it - a single shop visit
 * reasonably addresses everything flagged in the days leading up to it.
 */
@Component
public class MaintenanceReconciler {

    private final Duration responseWindow;

    public MaintenanceReconciler(@Value("${fleetpulse.maintenance.response-window}") Duration responseWindow) {
        this.responseWindow = responseWindow;
    }

    public List<ReconciliationEntry> reconcile(List<VehicleAlertRecord> alerts, List<WorkOrder> workOrders, Instant now) {
        List<WorkOrder> completedOrders = workOrders.stream()
                .filter(order -> order.getStatus() == WorkOrderStatus.COMPLETED && order.getCompletedAt() != null)
                .toList();

        return alerts.stream()
                .filter(alert -> alert.getSeverity() == AlertSeverity.SERVICE_NOW)
                .sorted(Comparator.comparing(VehicleAlertRecord::getRaisedAt))
                .map(alert -> classify(alert, completedOrders, now))
                .toList();
    }

    private ReconciliationEntry classify(VehicleAlertRecord alert, List<WorkOrder> completedOrders, Instant now) {
        Instant deadline = alert.getRaisedAt().plus(responseWindow);

        boolean resolved = completedOrders.stream()
                .anyMatch(order -> !order.getCompletedAt().isBefore(alert.getRaisedAt())
                        && !order.getCompletedAt().isAfter(deadline));

        ReconciliationStatus status;
        if (resolved) {
            status = ReconciliationStatus.RESOLVED;
        } else if (now.isAfter(deadline)) {
            status = ReconciliationStatus.OVERDUE;
        } else {
            status = ReconciliationStatus.PENDING;
        }

        return new ReconciliationEntry(alert.getVehicleId(), alert.getRiskScore(), alert.getRaisedAt(), status);
    }
}
