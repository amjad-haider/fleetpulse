package com.fleetpulse.maintenance.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class HealthAlertListener {

    private static final Logger log = LoggerFactory.getLogger(HealthAlertListener.class);

    private final VehicleAlertRecordRepository alertRecordRepository;

    public HealthAlertListener(VehicleAlertRecordRepository alertRecordRepository) {
        this.alertRecordRepository = alertRecordRepository;
    }

    @KafkaListener(topics = "${fleetpulse.alert.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onHealthAlert(HealthAlertEvent event) {
        try {
            VehicleAlertRecord record = VehicleAlertRecord.builder()
                    .vehicleId(event.vehicleId())
                    .riskScore(event.riskScore())
                    .severity(event.decision())
                    .raisedAt(event.raisedAt() != null ? event.raisedAt() : Instant.now())
                    .build();
            alertRecordRepository.save(record);
        } catch (Exception ex) {
            log.error("failed to record health alert for {}", event.vehicleId(), ex);
        }
    }
}
