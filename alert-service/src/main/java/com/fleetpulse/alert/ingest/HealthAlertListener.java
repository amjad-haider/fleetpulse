package com.fleetpulse.alert.ingest;

import com.fleetpulse.alert.notify.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class HealthAlertListener {

    private static final Logger log = LoggerFactory.getLogger(HealthAlertListener.class);

    private final FleetAlertRepository alertRepository;
    private final AlertCooldownPolicy cooldownPolicy;
    private final NotificationSender notificationSender;

    public HealthAlertListener(FleetAlertRepository alertRepository, AlertCooldownPolicy cooldownPolicy, NotificationSender notificationSender) {
        this.alertRepository = alertRepository;
        this.cooldownPolicy = cooldownPolicy;
        this.notificationSender = notificationSender;
    }

    @KafkaListener(topics = "${fleetpulse.alert.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onHealthAlert(HealthAlertEvent event) {
        try {
            Instant now = Instant.now();
            var recent = alertRepository.findByVehicleIdAndRaisedAtAfter(event.vehicleId(), now.minusSeconds(3600));
            boolean shouldNotify = cooldownPolicy.shouldNotify(recent, event.decision(), now);

            FleetAlert alert = FleetAlert.builder()
                    .vehicleId(event.vehicleId())
                    .riskScore(event.riskScore())
                    .severity(event.decision())
                    .raisedAt(event.raisedAt() != null ? event.raisedAt() : now)
                    .notificationSent(shouldNotify)
                    .build();
            alertRepository.save(alert);

            if (shouldNotify) {
                notificationSender.send(event.vehicleId(), event.riskScore(), event.decision());
            } else {
                log.debug("suppressing duplicate notification for {} (cooldown active)", event.vehicleId());
            }
        } catch (Exception ex) {
            log.error("failed to process health alert for {}", event.vehicleId(), ex);
        }
    }
}
