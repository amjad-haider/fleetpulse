package com.fleetpulse.health.scoring;

import com.fleetpulse.proto.health.v1.HealthDecision;
import com.fleetpulse.proto.health.v1.HealthScoreRequest;
import com.fleetpulse.proto.health.v1.HealthScoreResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Real Spring context on purpose, not a plain unit test with a manually
 * constructed orchestrator — @CircuitBreaker only does anything through the
 * Spring AOP proxy resilience4j-spring-boot3 wires up, so a test that
 * bypasses the container can't actually verify the circuit breaker behaves
 * correctly, only that the plain Java method body does.
 */
@SpringBootTest(properties = "grpc.server.port=19092")
@ActiveProfiles("test")
@Testcontainers
class RiskScoringOrchestratorIntegrationTest {

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

    @MockitoBean
    private OnnxRiskScorer onnxRiskScorer;

    @Autowired
    private RiskScoringOrchestrator orchestrator;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCircuit() {
        circuitBreakerRegistry.circuitBreaker("onnxScoring").reset();
    }

    private static HealthScoreRequest request(String vehicleId) {
        return HealthScoreRequest.newBuilder()
                .setVehicleId(vehicleId)
                .setEngineTempC(130)
                .setVibrationMmS(8)
                .setBrakeWearPct(100)
                .setFaultCodeCount(3)
                .build();
    }

    @Test
    void usesTheModelScoreWhenOnnxScoringSucceeds() {
        when(onnxRiskScorer.score(any())).thenReturn(0.42);

        HealthScoreResponse response = orchestrator.score(request("FLEET-ONNX-OK"));

        assertThat(response.getRiskScore()).isEqualTo(0.42);
        assertThat(response.getUsedFallback()).isFalse();
    }

    @Test
    void fallsBackToTheRuleBasedScorerWhenOnnxScoringThrows() {
        when(onnxRiskScorer.score(any())).thenThrow(new RuntimeException("model exploded"));

        // this request's fields are deliberately extreme (see request()), so
        // the rule-based scorer's own logic should land on SERVICE_NOW too
        HealthScoreResponse response = orchestrator.score(request("FLEET-ONNX-DOWN"));

        assertThat(response.getUsedFallback()).isTrue();
        assertThat(response.getDecision()).isEqualTo(HealthDecision.SERVICE_NOW);
    }

    @Test
    void openCircuitStopsCallingOnnxAtAllAndKeepsFallingBack() {
        when(onnxRiskScorer.score(any())).thenThrow(new RuntimeException("model exploded"));
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("onnxScoring");

        // sliding-window-size=20, minimum-number-of-calls=5, failure-rate-threshold=50
        // in application.yml -- five failures is enough to trip it open
        for (int i = 0; i < 5; i++) {
            orchestrator.score(request("FLEET-CIRCUIT-" + i));
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        HealthScoreResponse response = orchestrator.score(request("FLEET-CIRCUIT-AFTER-OPEN"));

        assertThat(response.getUsedFallback()).isTrue();
        // once open, resilience4j short-circuits before ever calling the
        // wrapped method again, so onnxRiskScorer should see exactly the 5
        // calls that tripped it, not 6
        verify(onnxRiskScorer, times(5)).score(any());
    }
}
