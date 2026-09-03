package com.fleetpulse.health.scoring;

import com.fleetpulse.proto.health.v1.HealthDecision;
import org.springframework.stereotype.Component;

/**
 * Rule-based risk scoring. This used to be the only scorer health-engine had
 * — now it's the fallback RiskScoringOrchestrator reaches for when the ONNX
 * model is unavailable, behind a circuit breaker. Only needs the four raw
 * reading fields, unlike the model, which is why it can keep working even
 * when Redis (where the rolling features live) is the thing that's down.
 */
@Component
public class RiskScorer {

    private static final double NORMAL_ENGINE_TEMP_C = 90.0;
    private static final double NORMAL_VIBRATION_MM_S = 2.0;
    private static final double MODERATE_BRAKE_WEAR_PCT = 50.0;
    private static final int SATURATING_FAULT_COUNT = 3;

    public ScoreResult score(RiskInput input) {
        double tempFactor = clamp((input.engineTempC() - NORMAL_ENGINE_TEMP_C) / 40.0);
        double vibrationFactor = clamp((input.vibrationMmS() - NORMAL_VIBRATION_MM_S) / 6.0);
        double brakeFactor = clamp((input.brakeWearPct() - MODERATE_BRAKE_WEAR_PCT) / 50.0);
        double faultFactor = clamp(input.faultCodeCount() / (double) SATURATING_FAULT_COUNT);

        double score = clamp(0.35 * tempFactor + 0.25 * vibrationFactor + 0.15 * brakeFactor + 0.25 * faultFactor);

        return new ScoreResult(RiskThresholds.round(score), RiskThresholds.decisionFor(score));
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
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
