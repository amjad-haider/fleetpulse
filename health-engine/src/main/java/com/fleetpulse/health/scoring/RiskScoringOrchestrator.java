package com.fleetpulse.health.scoring;

import com.fleetpulse.proto.health.v1.HealthScoreRequest;
import com.fleetpulse.proto.health.v1.HealthScoreResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The single place both the Kafka listener and the gRPC endpoint go through
 * to get a risk score. Tries the trained ONNX model first; if it's failing
 * or the circuit breaker has it open, falls back to the rule-based scorer
 * instead of just returning an error, so a bad model deployment or a
 * transient inference failure doesn't take fleet health monitoring down with
 * it.
 */
@Component
public class RiskScoringOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RiskScoringOrchestrator.class);

    private final OnnxRiskScorer onnxRiskScorer;
    private final RiskScorer ruleBasedScorer;

    public RiskScoringOrchestrator(OnnxRiskScorer onnxRiskScorer, RiskScorer ruleBasedScorer) {
        this.onnxRiskScorer = onnxRiskScorer;
        this.ruleBasedScorer = ruleBasedScorer;
    }

    @CircuitBreaker(name = "onnxScoring", fallbackMethod = "scoreWithRules")
    public HealthScoreResponse score(HealthScoreRequest request) {
        double riskScore = onnxRiskScorer.score(toFeatureVector(request));
        return HealthScoreResponse.newBuilder()
                .setVehicleId(request.getVehicleId())
                .setRiskScore(RiskThresholds.round(riskScore))
                .setDecision(RiskThresholds.decisionFor(riskScore))
                .setUsedFallback(false)
                .build();
    }

    // resilience4j invokes this by reflection when the circuit is open or
    // score(...) throws — signature has to be the same args plus the
    // Throwable that triggered it. Doesn't need to be public for that to work.
    @SuppressWarnings("unused")
    private HealthScoreResponse scoreWithRules(HealthScoreRequest request, Throwable throwable) {
        log.warn("ONNX scoring unavailable for {} ({}), falling back to the rule-based scorer",
                request.getVehicleId(), throwable.getMessage());

        RiskScorer.ScoreResult ruleResult = ruleBasedScorer.score(new RiskScorer.RiskInput(
                request.getEngineTempC(),
                request.getVibrationMmS(),
                request.getBrakeWearPct(),
                request.getFaultCodeCount()
        ));

        return HealthScoreResponse.newBuilder()
                .setVehicleId(request.getVehicleId())
                .setRiskScore(ruleResult.riskScore())
                .setDecision(ruleResult.decision())
                .setUsedFallback(true)
                .build();
    }

    private float[] toFeatureVector(HealthScoreRequest request) {
        return new float[]{
                (float) request.getEngineTempC(),
                (float) request.getVibrationMmS(),
                request.getRpm(),
                (float) request.getBrakeWearPct(),
                request.getFaultCodeCount(),
                (float) request.getOdometerKm(),
                (float) request.getAvgEngineTemp30D(),
                (float) request.getTempDeviation(),
                request.getFaultEvents24H()
        };
    }
}
