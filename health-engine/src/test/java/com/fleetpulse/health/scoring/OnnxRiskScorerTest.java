package com.fleetpulse.health.scoring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class OnnxRiskScorerTest {

    private final OnnxRiskScorer scorer = new OnnxRiskScorer("/models/risk_model.onnx");

    /**
     * Cross-language parity check: these expected scores were computed in
     * Python, directly from the same committed risk_model.onnx, using
     * onnxruntime's Python bindings (not sklearn — the actual exported
     * model). If the ONNX Runtime Java bindings ever produced a different
     * result than the Python ones for the same model and input, this is
     * where it would show up.
     */
    @Test
    void matchesThePythonComputedScoresForKnownInputs() {
        // healthy vehicle: normal temp, low wear, no faults
        assertThat(scorer.score(new float[]{88.0f, 1.8f, 1400, 10.0f, 0, 5000.0f, 88.0f, 0.0f, 0}))
                .isCloseTo(0.04243886470794678, within(1e-4));

        // moderate readings across the board, none of them individually alarming
        assertThat(scorer.score(new float[]{102.0f, 3.5f, 1500, 55.0f, 1, 120000.0f, 92.0f, 10.0f, 1}))
                .isCloseTo(0.027258694171905518, within(1e-4));

        // old, worn, hot, multiple faults: everything a real risk model should catch
        assertThat(scorer.score(new float[]{128.0f, 7.5f, 1600, 92.0f, 3, 260000.0f, 95.0f, 33.0f, 3}))
                .isCloseTo(0.993689775466919, within(1e-4));
    }

    @Test
    void scoreIsAlwaysAValidProbability() {
        double score = scorer.score(new float[]{95.0f, 2.5f, 1450, 30.0f, 0, 50000.0f, 90.0f, 5.0f, 0});

        assertThat(score).isBetween(0.0, 1.0);
    }
}
