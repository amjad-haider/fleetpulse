package com.fleetpulse.alert.ingest;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Every alert gets persisted for the audit trail, but a vehicle stuck at
 * MONITOR shouldn't page anyone again every few seconds. A notification only
 * goes out if nothing at least as severe fired for the same vehicle within
 * the cooldown window.
 */
@Component
public class AlertCooldownPolicy {

    private static final Duration COOLDOWN = Duration.ofMinutes(15);

    public boolean shouldNotify(List<FleetAlert> recentAlertsForVehicle, AlertSeverity newSeverity, Instant now) {
        Instant cutoff = now.minus(COOLDOWN);
        return recentAlertsForVehicle.stream()
                .filter(FleetAlert::isNotificationSent)
                .filter(a -> a.getRaisedAt().isAfter(cutoff))
                .noneMatch(a -> severityRank(a.getSeverity()) >= severityRank(newSeverity));
    }

    private int severityRank(AlertSeverity severity) {
        return switch (severity) {
            case OK -> 0;
            case MONITOR -> 1;
            case SERVICE_NOW -> 2;
        };
    }
}
