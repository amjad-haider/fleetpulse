package com.fleetpulse.health.ingest;

import com.fleetpulse.health.scoring.RiskScoringOrchestrator;
import com.fleetpulse.health.scoring.RollingFeatureStore;
import com.fleetpulse.health.scoring.RollingFeatures;
import com.fleetpulse.proto.health.v1.HealthDecision;
import com.fleetpulse.proto.health.v1.HealthScoreRequest;
import com.fleetpulse.proto.health.v1.HealthScoreResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryEventListenerTest {

    @Mock
    private RollingFeatureStore rollingFeatureStore;

    @Mock
    private RiskScoringOrchestrator scoringOrchestrator;

    @Mock
    private HealthScoreRepository scoreRepository;

    @Mock
    private HealthAlertPublisher alertPublisher;

    private TelemetryEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new TelemetryEventListener(rollingFeatureStore, scoringOrchestrator, scoreRepository, alertPublisher);
        when(rollingFeatureStore.featuresFor(any(), anyDouble(), any())).thenReturn(new RollingFeatures(90.0, 0));
    }

    private static TelemetryReadingEvent healthyReading() {
        return new TelemetryReadingEvent("FLEET-001", Instant.now(), 90, 2.0, 1500, 80, 10, 1000, List.of());
    }

    private static TelemetryReadingEvent criticalReading() {
        return new TelemetryReadingEvent("FLEET-002", Instant.now(), 130, 8, 1500, 80, 100, 1000,
                List.of("P0128", "P0301", "P0500"));
    }

    private static HealthScoreResponse okResponse(String vehicleId) {
        return HealthScoreResponse.newBuilder()
                .setVehicleId(vehicleId).setRiskScore(0.02).setDecision(HealthDecision.OK).setUsedFallback(false)
                .build();
    }

    private static HealthScoreResponse serviceNowResponse(String vehicleId) {
        return HealthScoreResponse.newBuilder()
                .setVehicleId(vehicleId).setRiskScore(0.95).setDecision(HealthDecision.SERVICE_NOW).setUsedFallback(true)
                .build();
    }

    @Test
    void healthyReadingIsPersistedButNotAlerted() {
        when(scoringOrchestrator.score(any())).thenReturn(okResponse("FLEET-001"));

        listener.onTelemetryReading(healthyReading());

        ArgumentCaptor<HealthScoreRecord> captor = ArgumentCaptor.forClass(HealthScoreRecord.class);
        verify(scoreRepository).save(captor.capture());
        assertThat(captor.getValue().getDecision()).isEqualTo(HealthDecision.OK);
        assertThat(captor.getValue().getVehicleId()).isEqualTo("FLEET-001");

        verify(alertPublisher, never()).publish(any());
    }

    @Test
    void criticalReadingIsPersistedAndAlerted() {
        when(scoringOrchestrator.score(any())).thenReturn(serviceNowResponse("FLEET-002"));

        listener.onTelemetryReading(criticalReading());

        ArgumentCaptor<HealthScoreRecord> recordCaptor = ArgumentCaptor.forClass(HealthScoreRecord.class);
        verify(scoreRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().isUsedFallback()).isTrue();

        ArgumentCaptor<HealthAlert> alertCaptor = ArgumentCaptor.forClass(HealthAlert.class);
        verify(alertPublisher).publish(alertCaptor.capture());
        assertThat(alertCaptor.getValue().vehicleId()).isEqualTo("FLEET-002");
        assertThat(alertCaptor.getValue().decision()).isEqualTo(HealthDecision.SERVICE_NOW);
    }

    @Test
    void buildsTheScoringRequestFromTheReadingAndRollingFeatures() {
        when(rollingFeatureStore.featuresFor(eq("FLEET-002"), anyDouble(), any())).thenReturn(new RollingFeatures(100.0, 4));
        when(scoringOrchestrator.score(any())).thenReturn(serviceNowResponse("FLEET-002"));

        listener.onTelemetryReading(criticalReading());

        ArgumentCaptor<HealthScoreRequest> captor = ArgumentCaptor.forClass(HealthScoreRequest.class);
        verify(scoringOrchestrator).score(captor.capture());
        HealthScoreRequest request = captor.getValue();
        assertThat(request.getVehicleId()).isEqualTo("FLEET-002");
        assertThat(request.getEngineTempC()).isEqualTo(130);
        assertThat(request.getFaultCodeCount()).isEqualTo(3);
        assertThat(request.getAvgEngineTemp30D()).isEqualTo(100.0);
        assertThat(request.getTempDeviation()).isEqualTo(30.0); // 130 current - 100 baseline
        assertThat(request.getFaultEvents24H()).isEqualTo(4);
    }

    @Test
    void recordsTheReadingIntoRollingHistoryAfterScoring() {
        when(scoringOrchestrator.score(any())).thenReturn(okResponse("FLEET-001"));

        listener.onTelemetryReading(healthyReading());

        verify(rollingFeatureStore).recordReading(eq("FLEET-001"), eq(90.0), eq(false), any());
    }

    @Test
    void recordsHasFaultCodeTrueWhenTheReadingCarriesFaultCodes() {
        when(scoringOrchestrator.score(any())).thenReturn(serviceNowResponse("FLEET-002"));

        listener.onTelemetryReading(criticalReading());

        verify(rollingFeatureStore).recordReading(eq("FLEET-002"), eq(130.0), eq(true), any());
    }

    @Test
    void aFailureScoringOneReadingDoesNotPropagate() {
        when(scoringOrchestrator.score(any())).thenThrow(new RuntimeException("orchestrator exploded"));

        // should not throw, the consumer keeps going for the rest of the fleet
        listener.onTelemetryReading(healthyReading());

        verify(scoreRepository, never()).save(any());
        verify(alertPublisher, never()).publish(any());
    }
}
