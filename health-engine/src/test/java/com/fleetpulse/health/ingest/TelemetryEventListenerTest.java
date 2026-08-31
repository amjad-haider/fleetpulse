package com.fleetpulse.health.ingest;

import com.fleetpulse.health.scoring.RiskScorer;
import com.fleetpulse.proto.health.v1.HealthDecision;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TelemetryEventListenerTest {

    @Mock
    private HealthScoreRepository scoreRepository;

    @Mock
    private HealthAlertPublisher alertPublisher;

    private TelemetryEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new TelemetryEventListener(new RiskScorer(), scoreRepository, alertPublisher);
    }

    private static TelemetryReadingEvent healthyReading() {
        return new TelemetryReadingEvent("FLEET-001", Instant.now(), 90, 2.0, 1500, 80, 10, 1000, List.of());
    }

    private static TelemetryReadingEvent criticalReading() {
        return new TelemetryReadingEvent("FLEET-002", Instant.now(), 130, 8, 1500, 80, 100, 1000,
                List.of("P0128", "P0301", "P0500"));
    }

    @Test
    void healthyReadingIsPersistedButNotAlerted() {
        listener.onTelemetryReading(healthyReading());

        ArgumentCaptor<HealthScoreRecord> captor = ArgumentCaptor.forClass(HealthScoreRecord.class);
        verify(scoreRepository).save(captor.capture());
        assertThat(captor.getValue().getDecision()).isEqualTo(HealthDecision.OK);
        assertThat(captor.getValue().getVehicleId()).isEqualTo("FLEET-001");

        verify(alertPublisher, never()).publish(any());
    }

    @Test
    void criticalReadingIsPersistedAndAlerted() {
        listener.onTelemetryReading(criticalReading());

        verify(scoreRepository).save(any(HealthScoreRecord.class));

        ArgumentCaptor<HealthAlert> alertCaptor = ArgumentCaptor.forClass(HealthAlert.class);
        verify(alertPublisher).publish(alertCaptor.capture());
        assertThat(alertCaptor.getValue().vehicleId()).isEqualTo("FLEET-002");
        assertThat(alertCaptor.getValue().decision()).isEqualTo(HealthDecision.SERVICE_NOW);
    }

    @Test
    void aFailureScoringOneReadingDoesNotPropagate() {
        org.mockito.Mockito.doThrow(new RuntimeException("db is down"))
                .when(scoreRepository).save(any(HealthScoreRecord.class));

        // should not throw, the consumer keeps going for the rest of the fleet
        listener.onTelemetryReading(healthyReading());

        verify(alertPublisher, never()).publish(any());
    }
}
