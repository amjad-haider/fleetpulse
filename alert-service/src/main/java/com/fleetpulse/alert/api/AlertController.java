package com.fleetpulse.alert.api;

import com.fleetpulse.alert.ingest.FleetAlertRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final FleetAlertRepository alertRepository;

    public AlertController(FleetAlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @GetMapping("/recent")
    public List<AlertResponse> recent() {
        return alertRepository.findTop50ByOrderByRaisedAtDesc().stream()
                .map(AlertResponse::from)
                .toList();
    }
}
