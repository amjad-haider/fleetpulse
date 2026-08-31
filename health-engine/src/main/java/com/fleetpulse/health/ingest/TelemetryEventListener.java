package com.fleetpulse.health.ingest;

import com.fleetpulse.health.scoring.RiskScorer;
import com.fleetpulse.proto.health.v1.HealthDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TelemetryEventListener {

    private static final Logger log = LoggerFactory.getLogger(TelemetryEventListener.class);

    private final RiskScorer riskScorer;
    private final HealthScoreRepository scoreRepository;
    private final HealthAlertPublisher alertPublisher;

    public TelemetryEventListener(RiskScorer riskScorer, HealthScoreRepository scoreRepository, HealthAlertPublisher alertPublisher) {
        this.riskScorer = riskScorer;
        this.scoreRepository = scoreRepository;
        this.alertPublisher = alertPublisher;
    }

    @KafkaListener(topics = "${fleetpulse.health.telemetry-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onTelemetryReading(TelemetryReadingEvent event) {
        try {
            RiskScorer.ScoreResult result = riskScorer.score(new RiskScorer.RiskInput(
                    event.engineTempC(),
                    event.vibrationMmS(),
                    event.brakeWearPct(),
                    event.faultCodes().size()
            ));

            scoreRepository.save(HealthScoreRecord.builder()
                    .vehicleId(event.vehicleId())
                    .riskScore(result.riskScore())
                    .decision(result.decision())
                    .build());

            if (result.decision() != HealthDecision.OK) {
                alertPublisher.publish(new HealthAlert(event.vehicleId(), result.riskScore(), result.decision(), Instant.now()));
            }
        } catch (Exception ex) {
            // one bad reading shouldn't stall the consumer group for the rest of the fleet
            log.error("failed to score telemetry reading for {}", event.vehicleId(), ex);
        }
    }
}
