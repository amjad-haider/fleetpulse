package com.fleetpulse.alert.ingest;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertCooldownPolicyTest {

    private final AlertCooldownPolicy policy = new AlertCooldownPolicy();
    private final Instant now = Instant.now();

    private FleetAlert alertMinutesAgo(int minutesAgo, AlertSeverity severity, boolean notified) {
        return FleetAlert.builder()
                .vehicleId("FLEET-001")
                .severity(severity)
                .raisedAt(now.minusSeconds(minutesAgo * 60L))
                .notificationSent(notified)
                .build();
    }

    @Test
    void notifiesWhenThereIsNoRecentHistory() {
        boolean result = policy.shouldNotify(List.of(), AlertSeverity.MONITOR, now);

        assertThat(result).isTrue();
    }

    @Test
    void suppressesWhenSameSeverityAlreadyNotifiedRecently() {
        var recent = List.of(alertMinutesAgo(5, AlertSeverity.MONITOR, true));

        boolean result = policy.shouldNotify(recent, AlertSeverity.MONITOR, now);

        assertThat(result).isFalse();
    }

    @Test
    void notifiesWhenSeverityEscalatesEvenDuringCooldown() {
        var recent = List.of(alertMinutesAgo(5, AlertSeverity.MONITOR, true));

        boolean result = policy.shouldNotify(recent, AlertSeverity.SERVICE_NOW, now);

        assertThat(result).isTrue();
    }

    @Test
    void suppressesLowerSeverityWhileAHigherOneIsStillActive() {
        var recent = List.of(alertMinutesAgo(5, AlertSeverity.SERVICE_NOW, true));

        boolean result = policy.shouldNotify(recent, AlertSeverity.MONITOR, now);

        assertThat(result).isFalse();
    }

    @Test
    void notifiesAgainOnceTheCooldownWindowHasPassed() {
        var recent = List.of(alertMinutesAgo(20, AlertSeverity.MONITOR, true));

        boolean result = policy.shouldNotify(recent, AlertSeverity.MONITOR, now);

        assertThat(result).isTrue();
    }

    @Test
    void ignoresAlertsThatNeverActuallyNotifiedWhenCheckingHistory() {
        // a suppressed alert shouldn't itself extend the cooldown
        var recent = List.of(alertMinutesAgo(5, AlertSeverity.MONITOR, false));

        boolean result = policy.shouldNotify(recent, AlertSeverity.MONITOR, now);

        assertThat(result).isTrue();
    }
}
