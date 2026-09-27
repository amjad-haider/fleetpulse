package com.fleetpulse.maintenance.workorder;

import com.fleetpulse.maintenance.ingest.AlertSeverity;
import com.fleetpulse.maintenance.ingest.VehicleAlertRecord;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceReconcilerTest {

    private static final Duration RESPONSE_WINDOW = Duration.ofDays(14);

    private final MaintenanceReconciler reconciler = new MaintenanceReconciler(RESPONSE_WINDOW);

    private static VehicleAlertRecord alert(String vehicleId, AlertSeverity severity, Instant raisedAt) {
        return VehicleAlertRecord.builder()
                .vehicleId(vehicleId)
                .riskScore(0.9)
                .severity(severity)
                .raisedAt(raisedAt)
                .build();
    }

    private static WorkOrder completedOrder(String vehicleId, Instant completedAt) {
        return WorkOrder.builder()
                .vehicleId(vehicleId)
                .description("brake service")
                .status(WorkOrderStatus.COMPLETED)
                .createdAt(completedAt.minus(1, ChronoUnit.DAYS))
                .completedAt(completedAt)
                .build();
    }

    @Test
    void aCompletedOrderWithinTheWindowResolvesTheAlert() {
        Instant now = Instant.now();
        Instant raisedAt = now.minus(5, ChronoUnit.DAYS);
        List<VehicleAlertRecord> alerts = List.of(alert("FLEET-001", AlertSeverity.SERVICE_NOW, raisedAt));
        List<WorkOrder> orders = List.of(completedOrder("FLEET-001", raisedAt.plus(2, ChronoUnit.DAYS)));

        List<ReconciliationEntry> result = reconciler.reconcile(alerts, orders, now);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(ReconciliationStatus.RESOLVED);
    }

    @Test
    void noCompletedOrderAndWindowElapsedIsOverdue() {
        Instant now = Instant.now();
        Instant raisedAt = now.minus(20, ChronoUnit.DAYS);
        List<VehicleAlertRecord> alerts = List.of(alert("FLEET-002", AlertSeverity.SERVICE_NOW, raisedAt));

        List<ReconciliationEntry> result = reconciler.reconcile(alerts, List.of(), now);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(ReconciliationStatus.OVERDUE);
    }

    @Test
    void noCompletedOrderButStillInsideTheWindowIsPending() {
        Instant now = Instant.now();
        Instant raisedAt = now.minus(3, ChronoUnit.DAYS);
        List<VehicleAlertRecord> alerts = List.of(alert("FLEET-003", AlertSeverity.SERVICE_NOW, raisedAt));

        List<ReconciliationEntry> result = reconciler.reconcile(alerts, List.of(), now);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(ReconciliationStatus.PENDING);
    }

    @Test
    void aCompletedOrderBeforeTheAlertDoesNotResolveIt() {
        Instant now = Instant.now();
        Instant raisedAt = now.minus(20, ChronoUnit.DAYS);
        List<VehicleAlertRecord> alerts = List.of(alert("FLEET-004", AlertSeverity.SERVICE_NOW, raisedAt));
        // this work order predates the alert entirely - unrelated maintenance
        List<WorkOrder> orders = List.of(completedOrder("FLEET-004", raisedAt.minus(10, ChronoUnit.DAYS)));

        List<ReconciliationEntry> result = reconciler.reconcile(alerts, orders, now);

        assertThat(result.get(0).status()).isEqualTo(ReconciliationStatus.OVERDUE);
    }

    @Test
    void anOpenWorkOrderDoesNotResolveTheAlert() {
        Instant now = Instant.now();
        Instant raisedAt = now.minus(3, ChronoUnit.DAYS);
        List<VehicleAlertRecord> alerts = List.of(alert("FLEET-005", AlertSeverity.SERVICE_NOW, raisedAt));
        WorkOrder open = WorkOrder.builder()
                .vehicleId("FLEET-005")
                .description("brake service")
                .status(WorkOrderStatus.OPEN)
                .createdAt(raisedAt.plus(1, ChronoUnit.DAYS))
                .build();

        List<ReconciliationEntry> result = reconciler.reconcile(alerts, List.of(open), now);

        // still inside the window, and nothing completed yet
        assertThat(result.get(0).status()).isEqualTo(ReconciliationStatus.PENDING);
    }

    @Test
    void oneCompletedOrderCanResolveSeveralRecentAlertsForTheSameVehicle() {
        Instant now = Instant.now();
        Instant firstAlert = now.minus(6, ChronoUnit.DAYS);
        Instant secondAlert = now.minus(4, ChronoUnit.DAYS);
        List<VehicleAlertRecord> alerts = List.of(
                alert("FLEET-006", AlertSeverity.SERVICE_NOW, firstAlert),
                alert("FLEET-006", AlertSeverity.SERVICE_NOW, secondAlert));
        List<WorkOrder> orders = List.of(completedOrder("FLEET-006", now.minus(2, ChronoUnit.DAYS)));

        List<ReconciliationEntry> result = reconciler.reconcile(alerts, orders, now);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(entry -> entry.status() == ReconciliationStatus.RESOLVED);
    }

    @Test
    void nonServiceNowAlertsAreIgnored() {
        Instant now = Instant.now();
        List<VehicleAlertRecord> alerts = List.of(
                alert("FLEET-007", AlertSeverity.OK, now.minus(1, ChronoUnit.DAYS)),
                alert("FLEET-007", AlertSeverity.MONITOR, now.minus(1, ChronoUnit.DAYS)));

        List<ReconciliationEntry> result = reconciler.reconcile(alerts, List.of(), now);

        assertThat(result).isEmpty();
    }
}
