package com.fleetpulse.health.ingest;

import com.fleetpulse.health.scoring.RiskScoringOrchestrator;
import com.fleetpulse.health.scoring.RollingFeatureStore;
import com.fleetpulse.health.scoring.RollingFeatures;
import com.fleetpulse.proto.health.v1.HealthDecision;
import com.fleetpulse.proto.health.v1.HealthScoreRequest;
import com.fleetpulse.proto.health.v1.HealthScoreResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TelemetryEventListener {

    private static final Logger log = LoggerFactory.getLogger(TelemetryEventListener.class);

    private final RollingFeatureStore rollingFeatureStore;
    private final RiskScoringOrchestrator scoringOrchestrator;
    private final HealthScoreRepository scoreRepository;
    private final HealthAlertPublisher alertPublisher;

    public TelemetryEventListener(
            RollingFeatureStore rollingFeatureStore,
            RiskScoringOrchestrator scoringOrchestrator,
            HealthScoreRepository scoreRepository,
            HealthAlertPublisher alertPublisher
    ) {
        this.rollingFeatureStore = rollingFeatureStore;
        this.scoringOrchestrator = scoringOrchestrator;
        this.scoreRepository = scoreRepository;
        this.alertPublisher = alertPublisher;
    }

    @KafkaListener(topics = "${fleetpulse.health.telemetry-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onTelemetryReading(TelemetryReadingEvent event) {
        try {
            Instant now = Instant.now();

            // features come from history *before* this reading, deliberately:
            // a reading can't meaningfully deviate from a baseline that
            // already includes itself
            RollingFeatures rollingFeatures = rollingFeatureStore.featuresFor(event.vehicleId(), event.engineTempC(), now);

            HealthScoreRequest request = HealthScoreRequest.newBuilder()
                    .setVehicleId(event.vehicleId())
                    .setEngineTempC(event.engineTempC())
                    .setVibrationMmS(event.vibrationMmS())
                    .setRpm(event.rpm())
                    .setBrakeWearPct(event.brakeWearPct())
                    .setFaultCodeCount(event.faultCodes().size())
                    .setOdometerKm(event.odometerKm())
                    .setAvgEngineTemp30D(rollingFeatures.avgEngineTemp30d())
                    .setTempDeviation(event.engineTempC() - rollingFeatures.avgEngineTemp30d())
                    .setFaultEvents24H((int) rollingFeatures.faultEvents24h())
                    .build();

            HealthScoreResponse response = scoringOrchestrator.score(request);

            scoreRepository.save(HealthScoreRecord.builder()
                    .vehicleId(event.vehicleId())
                    .riskScore(response.getRiskScore())
                    .decision(response.getDecision())
                    .usedFallback(response.getUsedFallback())
                    .build());

            if (response.getDecision() != HealthDecision.OK) {
                alertPublisher.publish(new HealthAlert(event.vehicleId(), response.getRiskScore(), response.getDecision(), now));
            }

            // recorded *after* scoring, so this reading feeds the next one's
            // baseline rather than its own
            boolean hasFaultCode = !event.faultCodes().isEmpty();
            rollingFeatureStore.recordReading(event.vehicleId(), event.engineTempC(), hasFaultCode, now);
        } catch (Exception ex) {
            // one bad reading shouldn't stall the consumer group for the rest of the fleet
            log.error("failed to score telemetry reading for {}", event.vehicleId(), ex);
        }
    }
}
