package com.fleetpulse.alert.notify;

import com.fleetpulse.alert.ingest.AlertSeverity;

public interface NotificationSender {

    void send(String vehicleId, double riskScore, AlertSeverity severity);
}
