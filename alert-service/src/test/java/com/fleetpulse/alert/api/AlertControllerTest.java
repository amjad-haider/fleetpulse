package com.fleetpulse.alert.api;

import com.fleetpulse.alert.ingest.AlertSeverity;
import com.fleetpulse.alert.ingest.FleetAlert;
import com.fleetpulse.alert.ingest.FleetAlertRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertController.class)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FleetAlertRepository alertRepository;

    @Test
    void returnsRecentAlertsMostRecentFirst() throws Exception {
        FleetAlert alert = FleetAlert.builder()
                .vehicleId("FLEET-001")
                .riskScore(0.8)
                .severity(AlertSeverity.SERVICE_NOW)
                .raisedAt(Instant.now())
                .notificationSent(true)
                .build();
        when(alertRepository.findTop50ByOrderByRaisedAtDesc()).thenReturn(List.of(alert));

        mockMvc.perform(get("/api/v1/alerts/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleId").value("FLEET-001"))
                .andExpect(jsonPath("$[0].severity").value("SERVICE_NOW"));
    }
}
