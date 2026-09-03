package com.fleetpulse.health.scoring;

import com.fleetpulse.proto.health.v1.HealthDecision;

/**
 * The score-to-decision bucketing both the rule-based scorer and the ONNX
 * model share, so a MONITOR from one means the same thing as a MONITOR from
 * the other.
 */
final class RiskThresholds {

    static final double MONITOR_THRESHOLD = 0.30;
    static final double SERVICE_NOW_THRESHOLD = 0.70;

    private RiskThresholds() {
    }

    static HealthDecision decisionFor(double score) {
        if (score >= SERVICE_NOW_THRESHOLD) {
            return HealthDecision.SERVICE_NOW;
        }
        if (score >= MONITOR_THRESHOLD) {
            return HealthDecision.MONITOR;
        }
        return HealthDecision.OK;
    }

    static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
