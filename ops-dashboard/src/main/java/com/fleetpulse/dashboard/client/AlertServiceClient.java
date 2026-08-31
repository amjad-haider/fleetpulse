package com.fleetpulse.dashboard.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class AlertServiceClient {

    private final RestClient restClient;

    public AlertServiceClient(RestClient.Builder restClientBuilder, @Value("${fleetpulse.services.alert-service-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public List<AlertDto> recentAlerts(String token) {
        return restClient.get()
                .uri("/api/v1/alerts/recent")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<List<AlertDto>>() {
                });
    }
}
