package com.fleetpulse.health.scoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RollingFeatureStoreTest {

    // a plain GenericContainer rather than a dedicated Testcontainers Redis
    // module — Redis doesn't need any special wait-strategy/setup beyond
    // "the port is open", so the generic one is all this needs
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static RollingFeatureStore store;
    static LettuceConnectionFactory connectionFactory;

    @BeforeAll
    static void startRedisAndStore() {
        redis.start();

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redis.getHost(), redis.getFirstMappedPort());
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();

        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        store = new RollingFeatureStore(template, objectMapper);
    }

    @AfterAll
    static void stopRedis() {
        connectionFactory.destroy();
        redis.stop();
    }

    @Test
    void aVehicleWithNoHistoryUsesItsCurrentReadingAsItsOwnBaseline() {
        RollingFeatures features = store.featuresFor("FLEET-NEW-001", 91.5, Instant.now());

        assertThat(features.avgEngineTemp30d()).isEqualTo(91.5);
        assertThat(features.faultEvents24h()).isZero();
    }

    @Test
    void averagesTemperatureAcrossRecordedReadings() {
        String vehicleId = "FLEET-AVG-001";
        Instant now = Instant.now();
        store.recordReading(vehicleId, 90.0, false, now.minus(2, ChronoUnit.HOURS));
        store.recordReading(vehicleId, 92.0, false, now.minus(1, ChronoUnit.HOURS));
        store.recordReading(vehicleId, 94.0, false, now);

        RollingFeatures features = store.featuresFor(vehicleId, 94.0, now);

        assertThat(features.avgEngineTemp30d()).isEqualTo(92.0);
    }

    @Test
    void countsOnlyFaultCodesWithinTheLast24Hours() {
        String vehicleId = "FLEET-FAULT-001";
        Instant now = Instant.now();
        store.recordReading(vehicleId, 90.0, true, now.minus(30, ChronoUnit.HOURS)); // outside the window
        store.recordReading(vehicleId, 90.0, true, now.minus(10, ChronoUnit.HOURS));
        store.recordReading(vehicleId, 90.0, false, now.minus(5, ChronoUnit.HOURS));
        store.recordReading(vehicleId, 90.0, true, now.minus(1, ChronoUnit.HOURS));

        RollingFeatures features = store.featuresFor(vehicleId, 90.0, now);

        assertThat(features.faultEvents24h()).isEqualTo(2);
    }

    @Test
    void excludesTemperatureReadingsOlderThanTheThirtyDayWindow() {
        String vehicleId = "FLEET-OLD-001";
        Instant now = Instant.now();
        store.recordReading(vehicleId, 200.0, false, now.minus(45, ChronoUnit.DAYS)); // way outside the window
        store.recordReading(vehicleId, 90.0, false, now);

        RollingFeatures features = store.featuresFor(vehicleId, 90.0, now);

        assertThat(features.avgEngineTemp30d()).isEqualTo(90.0);
    }

    @Test
    void differentVehiclesDoNotShareHistory() {
        Instant now = Instant.now();
        store.recordReading("FLEET-ISOLATION-A", 150.0, true, now);

        RollingFeatures otherVehicle = store.featuresFor("FLEET-ISOLATION-B", 88.0, now);

        assertThat(otherVehicle.avgEngineTemp30d()).isEqualTo(88.0);
        assertThat(otherVehicle.faultEvents24h()).isZero();
    }
}
