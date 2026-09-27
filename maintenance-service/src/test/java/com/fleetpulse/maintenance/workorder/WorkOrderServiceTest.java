package com.fleetpulse.maintenance.workorder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock
    private WorkOrderRepository workOrderRepository;

    private WorkOrderService service;

    @Test
    void createsAnOpenWorkOrderWithNoCompletionDate() {
        service = new WorkOrderService(workOrderRepository);
        when(workOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrder created = service.create("FLEET-001", "oil change");

        assertThat(created.getVehicleId()).isEqualTo("FLEET-001");
        assertThat(created.getStatus()).isEqualTo(WorkOrderStatus.OPEN);
        assertThat(created.getCompletedAt()).isNull();
        assertThat(created.getCreatedAt()).isNotNull();
    }

    @Test
    void movingToCompletedStampsACompletionTime() {
        service = new WorkOrderService(workOrderRepository);
        UUID id = UUID.randomUUID();
        WorkOrder existing = WorkOrder.builder()
                .id(id).vehicleId("FLEET-002").description("brake service")
                .status(WorkOrderStatus.IN_PROGRESS).createdAt(Instant.now())
                .build();
        when(workOrderRepository.findById(id)).thenReturn(Optional.of(existing));
        when(workOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrder result = service.updateStatus(id, WorkOrderStatus.COMPLETED);

        assertThat(result.getStatus()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    void movingAwayFromCompletedClearsTheCompletionTime() {
        service = new WorkOrderService(workOrderRepository);
        UUID id = UUID.randomUUID();
        WorkOrder existing = WorkOrder.builder()
                .id(id).vehicleId("FLEET-003").description("brake service")
                .status(WorkOrderStatus.COMPLETED).createdAt(Instant.now()).completedAt(Instant.now())
                .build();
        when(workOrderRepository.findById(id)).thenReturn(Optional.of(existing));
        when(workOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrder result = service.updateStatus(id, WorkOrderStatus.IN_PROGRESS);

        assertThat(result.getCompletedAt()).isNull();
    }

    @Test
    void updatingAnUnknownWorkOrderThrows() {
        service = new WorkOrderService(workOrderRepository);
        UUID id = UUID.randomUUID();
        when(workOrderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(id, WorkOrderStatus.COMPLETED))
                .isInstanceOf(WorkOrderNotFoundException.class);
    }
}
