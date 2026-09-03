package com.fleetpulse.health.ingest;

import com.fleetpulse.health.scoring.RollingFeatureStore;
import com.fleetpulse.proto.health.v1.HealthDecision;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "grpc.server.port=19091")
@Testcontainers
class HealthEngineIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getFirstMappedPort().toString());
    }

    @Autowired
    private HealthScoreRepository scoreRepository;

    @Autowired
    private RollingFeatureStore rollingFeatureStore;

    private KafkaTemplate<String, TelemetryReadingEvent> telemetryProducer;
    private Consumer<String, HealthAlert> alertConsumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = new HashMap<>(KafkaTestUtils.producerProps(kafka.getBootstrapServers()));
        var producerFactory = new DefaultKafkaProducerFactory<String, TelemetryReadingEvent>(
                producerProps, new StringSerializer(), new JsonSerializer<>());
        telemetryProducer = new KafkaTemplate<>(producerFactory);

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), "alert-test-group", "true");
        JsonDeserializer<HealthAlert> valueDeserializer = new JsonDeserializer<>(HealthAlert.class, false);
        alertConsumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), valueDeserializer)
                .createConsumer();
        alertConsumer.subscribe(List.of("fleet.health.alerts"));
        alertConsumer.poll(Duration.ofMillis(100)); // force partition assignment before the real poll later
    }

    @AfterEach
    void tearDown() {
        alertConsumer.close();
    }

    @Test
    void criticalReadingGetsScoredPersistedAndAlerted() {
        // the trained model learned realistic feature correlations from
        // training data where high brake wear / fault counts only show up on
        // high-mileage vehicles, so a fresh, unrealistic combination (worn
        // brakes on a near-new vehicle, no history) reads as low-risk to it,
        // correctly. Recorded history is what a genuinely aging, currently
        // spiking vehicle actually looks like, matching a combination
        // verified in OnnxRiskScorerTest to score ~0.99 for the exact same model.
        String vehicleId = "FLEET-INTEG-999";
        Instant now = Instant.now();
        rollingFeatureStore.recordReading(vehicleId, 95.0, true, now.minus(2, ChronoUnit.HOURS));
        rollingFeatureStore.recordReading(vehicleId, 95.0, true, now.minus(1, ChronoUnit.HOURS));
        rollingFeatureStore.recordReading(vehicleId, 95.0, true, now.minus(30, ChronoUnit.MINUTES));

        TelemetryReadingEvent reading = new TelemetryReadingEvent(
                vehicleId, now, 128, 7.5, 1600, 50, 92, 260_000,
                List.of("P0128", "P0301", "P0500"));

        telemetryProducer.send("telemetry.readings", reading.vehicleId(), reading);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<HealthScoreRecord> scores = scoreRepository.findAll().stream()
                    .filter(r -> r.getVehicleId().equals("FLEET-INTEG-999"))
                    .toList();
            assertThat(scores).hasSize(1);
            assertThat(scores.get(0).getDecision()).isEqualTo(HealthDecision.SERVICE_NOW);
        });

        var alertRecords = KafkaTestUtils.getRecords(alertConsumer, Duration.ofSeconds(10), 1);
        assertThat(alertRecords.count()).isEqualTo(1);
        HealthAlert alert = alertRecords.iterator().next().value();
        assertThat(alert.vehicleId()).isEqualTo("FLEET-INTEG-999");
        assertThat(alert.decision()).isEqualTo(HealthDecision.SERVICE_NOW);
    }
}
