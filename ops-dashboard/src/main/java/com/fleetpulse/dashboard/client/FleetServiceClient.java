package com.fleetpulse.dashboard.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class FleetServiceClient {

    private final RestClient restClient;

    public FleetServiceClient(RestClient.Builder restClientBuilder, @Value("${fleetpulse.services.fleet-service-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public LoginResponse login(String email, String password) {
        return restClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, password))
                .retrieve()
                .body(LoginResponse.class);
    }

    public List<VehicleDto> listVehicles(String token) {
        return restClient.get()
                .uri("/api/v1/vehicles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<List<VehicleDto>>() {
                });
    }
}
