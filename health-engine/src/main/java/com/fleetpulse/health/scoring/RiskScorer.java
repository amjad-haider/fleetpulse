package com.fleetpulse.health.scoring;

import com.fleetpulse.proto.health.v1.HealthDecision;
import org.springframework.stereotype.Component;

/**
 * Rule-based risk scoring. There's no trained model yet (that's the Python
 * ml-training pipeline, still to come) — this is deliberately the same
 * fallback logic that'll eventually sit behind a circuit breaker once a real
 * model is serving, not a placeholder to throw away.
 */
@Component
public class RiskScorer {

    private static final double NORMAL_ENGINE_TEMP_C = 90.0;
    private static final double NORMAL_VIBRATION_MM_S = 2.0;
    private static final double MODERATE_BRAKE_WEAR_PCT = 50.0;
    private static final int SATURATING_FAULT_COUNT = 3;

    private static final double MONITOR_THRESHOLD = 0.30;
    private static final double SERVICE_NOW_THRESHOLD = 0.70;

    public ScoreResult score(RiskInput input) {
        double tempFactor = clamp((input.engineTempC() - NORMAL_ENGINE_TEMP_C) / 40.0);
        double vibrationFactor = clamp((input.vibrationMmS() - NORMAL_VIBRATION_MM_S) / 6.0);
        double brakeFactor = clamp((input.brakeWearPct() - MODERATE_BRAKE_WEAR_PCT) / 50.0);
        double faultFactor = clamp(input.faultCodeCount() / (double) SATURATING_FAULT_COUNT);

        double score = 0.35 * tempFactor + 0.25 * vibrationFactor + 0.15 * brakeFactor + 0.25 * faultFactor;
        score = clamp(score);

        HealthDecision decision;
        if (score >= SERVICE_NOW_THRESHOLD) {
            decision = HealthDecision.SERVICE_NOW;
        } else if (score >= MONITOR_THRESHOLD) {
            decision = HealthDecision.MONITOR;
        } else {
            decision = HealthDecision.OK;
        }

        return new ScoreResult(round(score), decision);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    public record RiskInput(
            double engineTempC,
            double vibrationMmS,
            double brakeWearPct,
            int faultCodeCount
    ) {
    }

    public record ScoreResult(double riskScore, HealthDecision decision) {
    }
}
