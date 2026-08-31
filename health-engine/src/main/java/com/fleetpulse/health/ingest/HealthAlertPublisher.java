package com.fleetpulse.health.ingest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class HealthAlertPublisher {

    private final KafkaTemplate<String, HealthAlert> kafkaTemplate;
    private final String topic;

    public HealthAlertPublisher(
            KafkaTemplate<String, HealthAlert> kafkaTemplate,
            @Value("${fleetpulse.health.alert-topic}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(HealthAlert alert) {
        kafkaTemplate.send(topic, alert.vehicleId(), alert);
    }
}
