package com.fleetpulse.alert.ingest;

import com.fleetpulse.alert.notify.NotificationSender;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthAlertListenerTest {

    @Mock
    private FleetAlertRepository alertRepository;

    @Mock
    private AlertCooldownPolicy cooldownPolicy;

    @Mock
    private NotificationSender notificationSender;

    private HealthAlertListener listener;

    @BeforeEach
    void setUp() {
        listener = new HealthAlertListener(alertRepository, cooldownPolicy, notificationSender);
    }

    @Test
    void persistsEveryAlertAndNotifiesWhenPolicyAllowsIt() {
        when(alertRepository.findByVehicleIdAndRaisedAtAfter(eq("FLEET-001"), any())).thenReturn(List.of());
        when(cooldownPolicy.shouldNotify(any(), eq(AlertSeverity.SERVICE_NOW), any())).thenReturn(true);

        listener.onHealthAlert(new HealthAlertEvent("FLEET-001", 0.9, AlertSeverity.SERVICE_NOW, Instant.now()));

        ArgumentCaptor<FleetAlert> captor = ArgumentCaptor.forClass(FleetAlert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().isNotificationSent()).isTrue();

        verify(notificationSender).send("FLEET-001", 0.9, AlertSeverity.SERVICE_NOW);
    }

    @Test
    void persistsButDoesNotNotifyWhenPolicySuppressesIt() {
        when(alertRepository.findByVehicleIdAndRaisedAtAfter(anyString(), any())).thenReturn(List.of());
        when(cooldownPolicy.shouldNotify(any(), any(), any())).thenReturn(false);

        listener.onHealthAlert(new HealthAlertEvent("FLEET-002", 0.4, AlertSeverity.MONITOR, Instant.now()));

        ArgumentCaptor<FleetAlert> captor = ArgumentCaptor.forClass(FleetAlert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().isNotificationSent()).isFalse();

        verify(notificationSender, never()).send(anyString(), anyDouble(), any(AlertSeverity.class));
    }

    @Test
    void aFailureDoesNotPropagateOutOfTheListener() {
        when(alertRepository.findByVehicleIdAndRaisedAtAfter(anyString(), any()))
                .thenThrow(new RuntimeException("db is down"));

        listener.onHealthAlert(new HealthAlertEvent("FLEET-003", 0.5, AlertSeverity.MONITOR, Instant.now()));

        verify(notificationSender, never()).send(anyString(), anyDouble(), any(AlertSeverity.class));
    }
}
