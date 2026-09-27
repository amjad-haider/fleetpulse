package com.fleetpulse.maintenance.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetpulse.maintenance.workorder.ReconciliationEntry;
import com.fleetpulse.maintenance.workorder.ReconciliationService;
import com.fleetpulse.maintenance.workorder.ReconciliationStatus;
import com.fleetpulse.maintenance.workorder.WorkOrder;
import com.fleetpulse.maintenance.workorder.WorkOrderNotFoundException;
import com.fleetpulse.maintenance.workorder.WorkOrderService;
import com.fleetpulse.maintenance.workorder.WorkOrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@WebMvcTest(WorkOrderController.class)
class WorkOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WorkOrderService workOrderService;

    @MockitoBean
    private ReconciliationService reconciliationService;

    private static WorkOrder aWorkOrder() {
        return WorkOrder.builder()
                .id(UUID.randomUUID())
                .vehicleId("FLEET-001")
                .description("brake pad replacement")
                .status(WorkOrderStatus.OPEN)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void createsAWorkOrder() throws Exception {
        WorkOrder created = aWorkOrder();
        when(workOrderService.create("FLEET-001", "brake pad replacement")).thenReturn(created);

        mockMvc.perform(post("/api/v1/work-orders")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateWorkOrderRequest("FLEET-001", "brake pad replacement"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicleId").value("FLEET-001"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void rejectsAWorkOrderWithNoVehicleId() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateWorkOrderRequest("", "brake pad replacement"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesStatus() throws Exception {
        UUID id = UUID.randomUUID();
        WorkOrder completed = aWorkOrder();
        completed.setId(id);
        completed.setStatus(WorkOrderStatus.COMPLETED);
        completed.setCompletedAt(Instant.now());
        when(workOrderService.updateStatus(eq(id), eq(WorkOrderStatus.COMPLETED))).thenReturn(completed);

        mockMvc.perform(patch("/api/v1/work-orders/{id}/status", id)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateWorkOrderStatusRequest(WorkOrderStatus.COMPLETED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void updatingAnUnknownWorkOrderIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(workOrderService.updateStatus(eq(id), any())).thenThrow(new WorkOrderNotFoundException(id));

        mockMvc.perform(patch("/api/v1/work-orders/{id}/status", id)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateWorkOrderStatusRequest(WorkOrderStatus.COMPLETED))))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsRecentWorkOrders() throws Exception {
        when(workOrderService.recent()).thenReturn(List.of(aWorkOrder()));

        mockMvc.perform(get("/api/v1/work-orders/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleId").value("FLEET-001"));
    }

    @Test
    void returnsTheReconciliationReport() throws Exception {
        when(reconciliationService.reconcileFleet()).thenReturn(
                List.of(new ReconciliationEntry("FLEET-001", 0.95, Instant.now(), ReconciliationStatus.OVERDUE)));

        mockMvc.perform(get("/api/v1/work-orders/reconciliation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleId").value("FLEET-001"))
                .andExpect(jsonPath("$[0].status").value("OVERDUE"));
    }
}
