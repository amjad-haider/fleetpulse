package com.fleetpulse.health.scoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Keeps a rolling history of readings per vehicle in Redis, and computes the
 * model's rolling-window features from it. Real calendar-time windows (30
 * days, 24 hours), not an approximation — a freshly-registered vehicle just
 * naturally has a short window filled so far, same as any real fleet system
 * would on day one.
 *
 * Backed by a capped Redis list per vehicle rather than a sorted set: simpler
 * to get right (no risk of two readings landing on the same score/member),
 * and reading the whole list back for a vehicle at this data scale is cheap.
 */
@Component
public class RollingFeatureStore {

    private static final Logger log = LoggerFactory.getLogger(RollingFeatureStore.class);

    // defensive cap so a vehicle that never stops reporting can't grow its
    // key without bound, independent of the time-window logic below
    private static final long MAX_ENTRIES_PER_VEHICLE = 20_000;
    private static final Duration KEY_TTL = Duration.ofDays(35);
    private static final Duration AVG_TEMP_WINDOW = Duration.ofDays(30);
    private static final Duration FAULT_WINDOW = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RollingFeatureStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void recordReading(String vehicleId, double engineTempC, boolean hasFaultCode, Instant recordedAt) {
        String key = keyFor(vehicleId);
        ReadingEntry entry = new ReadingEntry(engineTempC, hasFaultCode, recordedAt.toEpochMilli());
        redisTemplate.opsForList().rightPush(key, toJson(entry));
        redisTemplate.opsForList().trim(key, -MAX_ENTRIES_PER_VEHICLE, -1);
        redisTemplate.expire(key, KEY_TTL);
    }

    public RollingFeatures featuresFor(String vehicleId, double currentEngineTempC, Instant now) {
        List<String> raw = redisTemplate.opsForList().range(keyFor(vehicleId), 0, -1);
        if (raw == null || raw.isEmpty()) {
            return RollingFeatures.empty(currentEngineTempC);
        }

        long avgWindowStart = now.minus(AVG_TEMP_WINDOW).toEpochMilli();
        long faultWindowStart = now.minus(FAULT_WINDOW).toEpochMilli();

        double tempSum = 0;
        long tempCount = 0;
        long faultCount = 0;

        for (String json : raw) {
            ReadingEntry entry = fromJson(json);
            if (entry == null) {
                continue; // one corrupt entry shouldn't blow up scoring for the whole vehicle
            }
            if (entry.timestamp() >= avgWindowStart) {
                tempSum += entry.engineTempC();
                tempCount++;
            }
            if (entry.hasFaultCode() && entry.timestamp() >= faultWindowStart) {
                faultCount++;
            }
        }

        double avgTemp = tempCount > 0 ? tempSum / tempCount : currentEngineTempC;
        return new RollingFeatures(avgTemp, faultCount);
    }

    private String keyFor(String vehicleId) {
        return "health:readings:" + vehicleId;
    }

    private String toJson(ReadingEntry entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize reading entry", ex);
        }
    }

    private ReadingEntry fromJson(String json) {
        try {
            return objectMapper.readValue(json, ReadingEntry.class);
        } catch (Exception ex) {
            log.warn("skipping unparseable rolling-feature entry: {}", ex.getMessage());
            return null;
        }
    }

    private record ReadingEntry(double engineTempC, boolean hasFaultCode, long timestamp) {
    }
}
