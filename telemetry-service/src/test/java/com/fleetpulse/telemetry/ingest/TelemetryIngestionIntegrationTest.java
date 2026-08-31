package com.fleetpulse.telemetry.ingest;

import com.fleetpulse.proto.telemetry.v1.TelemetryAck;
import com.fleetpulse.proto.telemetry.v1.TelemetryIngestionGrpc;
import com.fleetpulse.proto.telemetry.v1.TelemetryRecord;
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "grpc.server.port=19090")
@Testcontainers
class TelemetryIngestionIntegrationTest {

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
    private TelemetryReadingRepository readingRepository;

    private ManagedChannel channel;
    private TelemetryIngestionGrpc.TelemetryIngestionStub stub;
    private Consumer<String, TelemetryEvent> kafkaConsumer;

    @BeforeEach
    void setUp() {
        channel = ManagedChannelBuilder.forAddress("localhost", 19090).usePlaintext().build();
        stub = TelemetryIngestionGrpc.newStub(channel);

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), "telemetry-test-group", "true");
        JsonDeserializer<TelemetryEvent> valueDeserializer = new JsonDeserializer<>(TelemetryEvent.class, false);
        kafkaConsumer = new DefaultKafkaConsumerFactory<>(consumerProps, new org.apache.kafka.common.serialization.StringDeserializer(), valueDeserializer)
                .createConsumer();
        kafkaConsumer.subscribe(List.of("telemetry.readings"));
        kafkaConsumer.poll(Duration.ofMillis(100)); // force initial partition assignment before the real poll later
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        kafkaConsumer.close();
    }

    @Test
    void streamedRecordsArePersistedAndPublishedToKafka() throws InterruptedException {
        CountDownLatch completedLatch = new CountDownLatch(1);
        AtomicReference<TelemetryAck> lastAck = new AtomicReference<>();

        StreamObserver<TelemetryRecord> requestStream = stub.streamTelemetry(new StreamObserver<>() {
            @Override
            public void onNext(TelemetryAck ack) {
                lastAck.set(ack);
            }

            @Override
            public void onError(Throwable t) {
                completedLatch.countDown();
            }

            @Override
            public void onCompleted() {
                completedLatch.countDown();
            }
        });

        for (int i = 0; i < 5; i++) {
            requestStream.onNext(buildRecord("FLEET-INTEG-001"));
        }
        requestStream.onCompleted();

        assertThat(completedLatch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(lastAck.get()).isNotNull();
        assertThat(lastAck.get().getVehicleId()).isEqualTo("FLEET-INTEG-001");
        assertThat(lastAck.get().getRecordsReceived()).isEqualTo(5);

        List<TelemetryReading> persisted = readingRepository.findAll();
        assertThat(persisted).hasSize(5);
        assertThat(persisted).allMatch(r -> r.getVehicleId().equals("FLEET-INTEG-001"));

        // one Kafka event per record, all five, not just the ones that got an ack
        var records = KafkaTestUtils.getRecords(kafkaConsumer, Duration.ofSeconds(10), 5);
        assertThat(records.count()).isEqualTo(5);
        for (ConsumerRecord<String, TelemetryEvent> record : records) {
            assertThat(record.key()).isEqualTo("FLEET-INTEG-001");
            assertThat(record.value().vehicleId()).isEqualTo("FLEET-INTEG-001");
        }
    }

    private static TelemetryRecord buildRecord(String vehicleId) {
        Instant now = Instant.now();
        return TelemetryRecord.newBuilder()
                .setVehicleId(vehicleId)
                .setRecordedAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build())
                .setEngineTempC(91.2)
                .setVibrationMmS(2.4)
                .setRpm(1600)
                .setFuelLevelPct(75)
                .setBrakeWearPct(12)
                .setOdometerKm(2000)
                .build();
    }
}
