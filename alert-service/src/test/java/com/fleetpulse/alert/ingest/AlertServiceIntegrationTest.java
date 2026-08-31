package com.fleetpulse.alert.ingest;

import com.fleetpulse.alert.notify.NotificationSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Testcontainers
class AlertServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private FleetAlertRepository alertRepository;

    @MockitoBean
    private NotificationSender notificationSender;

    private KafkaTemplate<String, HealthAlertEvent> producer;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = new HashMap<>(KafkaTestUtils.producerProps(kafka.getBootstrapServers()));
        var producerFactory = new DefaultKafkaProducerFactory<String, HealthAlertEvent>(
                producerProps, new org.apache.kafka.common.serialization.StringSerializer(), new JsonSerializer<>());
        producer = new KafkaTemplate<>(producerFactory);
    }

    @Test
    void alertFromKafkaIsPersistedAndTriggersNotification() {
        HealthAlertEvent event = new HealthAlertEvent("FLEET-INTEG-777", 0.85, AlertSeverity.SERVICE_NOW, Instant.now());

        producer.send("fleet.health.alerts", event.vehicleId(), event);

        await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
            List<FleetAlert> alerts = alertRepository.findByVehicleIdAndRaisedAtAfter(
                    "FLEET-INTEG-777", Instant.now().minusSeconds(3600));
            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).isNotificationSent()).isTrue();
        });

        verify(notificationSender).send(anyString(), anyDouble(), org.mockito.ArgumentMatchers.eq(AlertSeverity.SERVICE_NOW));
    }
}
