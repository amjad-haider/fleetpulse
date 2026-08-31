package com.fleetpulse.alert.notify;

import com.fleetpulse.alert.ingest.AlertSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * There's no real email/SMS provider wired up (no Twilio/SendGrid account for
 * a side project), so this just logs what would have gone out. Swapping in a
 * real provider later means implementing NotificationSender and replacing
 * this bean, nothing else in the service needs to change.
 */
@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(String vehicleId, double riskScore, AlertSeverity severity) {
        log.warn("[notify] {} risk={} severity={} -> would alert fleet ops team", vehicleId, riskScore, severity);
    }
}
