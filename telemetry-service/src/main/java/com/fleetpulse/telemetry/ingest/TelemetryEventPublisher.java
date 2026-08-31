package com.fleetpulse.telemetry.ingest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TelemetryEventPublisher {

    private final KafkaTemplate<String, TelemetryEvent> kafkaTemplate;
    private final String topic;

    public TelemetryEventPublisher(
            KafkaTemplate<String, TelemetryEvent> kafkaTemplate,
            @Value("${fleetpulse.telemetry.topic}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(TelemetryReading reading) {
        // keyed by vehicle id so all of one vehicle's events land on the same
        // partition and stay in order for whatever consumes them downstream
        kafkaTemplate.send(topic, reading.getVehicleId(), TelemetryEvent.from(reading));
    }
}
