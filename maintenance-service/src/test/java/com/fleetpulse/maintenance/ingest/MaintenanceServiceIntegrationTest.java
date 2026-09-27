package com.fleetpulse.maintenance.ingest;

import com.fleetpulse.maintenance.workorder.ReconciliationEntry;
import com.fleetpulse.maintenance.workorder.ReconciliationService;
import com.fleetpulse.maintenance.workorder.ReconciliationStatus;
import com.fleetpulse.maintenance.workorder.WorkOrder;
import com.fleetpulse.maintenance.workorder.WorkOrderRepository;
import com.fleetpulse.maintenance.workorder.WorkOrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
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

/**
 * Exercises the real path: a SERVICE_NOW alert arrives over Kafka (exactly
 * as health-engine publishes it), gets recorded, and a subsequently
 * completed work order for the same vehicle resolves it in the
 * reconciliation report - against real Postgres and Kafka, not mocks.
 */
@SpringBootTest
@Testcontainers
class MaintenanceServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @org.springframework.test.context.DynamicPropertySource
    static void registerProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private VehicleAlertRecordRepository alertRecordRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private ReconciliationService reconciliationService;

    private KafkaTemplate<String, HealthAlertEvent> producer;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = new HashMap<>(org.springframework.kafka.test.utils.KafkaTestUtils.producerProps(kafka.getBootstrapServers()));
        var producerFactory = new DefaultKafkaProducerFactory<String, HealthAlertEvent>(
                producerProps, new org.apache.kafka.common.serialization.StringSerializer(), new JsonSerializer<>());
        producer = new KafkaTemplate<>(producerFactory);
    }

    @Test
    void alertFromKafkaGetsResolvedByALaterCompletedWorkOrder() {
        String vehicleId = "FLEET-INTEG-MAINT-1";
        Instant raisedAt = Instant.now();
        HealthAlertEvent event = new HealthAlertEvent(vehicleId, 0.95, AlertSeverity.SERVICE_NOW, raisedAt);

        producer.send("fleet.health.alerts", vehicleId, event);

        await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
            List<VehicleAlertRecord> records = alertRecordRepository.findByVehicleId(vehicleId);
            assertThat(records).hasSize(1);
        });

        WorkOrder completed = WorkOrder.builder()
                .vehicleId(vehicleId)
                .description("replaced brake pads")
                .status(WorkOrderStatus.COMPLETED)
                .createdAt(raisedAt.plusSeconds(60))
                .completedAt(raisedAt.plusSeconds(120))
                .build();
        workOrderRepository.save(completed);

        List<ReconciliationEntry> entries = reconciliationService.reconcileFleet();

        assertThat(entries)
                .filteredOn(entry -> entry.vehicleId().equals(vehicleId))
                .hasSize(1)
                .allMatch(entry -> entry.status() == ReconciliationStatus.RESOLVED);
    }
}
