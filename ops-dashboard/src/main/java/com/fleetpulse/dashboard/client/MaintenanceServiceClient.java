package com.fleetpulse.dashboard.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class MaintenanceServiceClient {

    private final RestClient restClient;

    public MaintenanceServiceClient(RestClient.Builder restClientBuilder, @Value("${fleetpulse.services.maintenance-service-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public List<WorkOrderDto> recentWorkOrders(String token) {
        return restClient.get()
                .uri("/api/v1/work-orders/recent")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<List<WorkOrderDto>>() {
                });
    }

    public List<ReconciliationEntryDto> reconciliation(String token) {
        return restClient.get()
                .uri("/api/v1/work-orders/reconciliation")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ReconciliationEntryDto>>() {
                });
    }

    public WorkOrderDto createWorkOrder(String token, String vehicleId, String description) {
        return restClient.post()
                .uri("/api/v1/work-orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateWorkOrderRequest(vehicleId, description))
                .retrieve()
                .body(WorkOrderDto.class);
    }
}
