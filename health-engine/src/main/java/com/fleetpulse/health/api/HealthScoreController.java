package com.fleetpulse.health.api;

import com.fleetpulse.health.ingest.HealthScoreRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/health-scores")
public class HealthScoreController {

    private final HealthScoreRepository scoreRepository;

    public HealthScoreController(HealthScoreRepository scoreRepository) {
        this.scoreRepository = scoreRepository;
    }

    @GetMapping("/recent")
    public List<HealthScoreResponse> recent() {
        return scoreRepository.findTop50ByOrderByScoredAtDesc().stream()
                .map(HealthScoreResponse::from)
                .toList();
    }
}
