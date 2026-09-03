package com.fleetpulse.health.scoring;

/**
 * The features the trained model needs beyond a single raw reading — a
 * vehicle's own recent baseline, and how often it's been throwing faults
 * lately. Both are genuine sliding time windows (30 days, 24 hours), not a
 * "last N readings" approximation, computed from RollingFeatureStore.
 */
public record RollingFeatures(double avgEngineTemp30d, long faultEvents24h) {

    public static RollingFeatures empty(double currentEngineTempC) {
        // a vehicle with no history yet: its own current reading is the only
        // reasonable stand-in for "its recent baseline"
        return new RollingFeatures(currentEngineTempC, 0);
    }
}
