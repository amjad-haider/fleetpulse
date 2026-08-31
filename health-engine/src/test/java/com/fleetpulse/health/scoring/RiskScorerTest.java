package com.fleetpulse.health.scoring;

import com.fleetpulse.proto.health.v1.HealthDecision;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiskScorerTest {

    private final RiskScorer scorer = new RiskScorer();

    @Test
    void healthyVehicleScoresLowAndIsOk() {
        var result = scorer.score(new RiskScorer.RiskInput(90, 2.0, 10, 0));

        assertThat(result.riskScore()).isEqualTo(0.0);
        assertThat(result.decision()).isEqualTo(HealthDecision.OK);
    }

    @Test
    void highEngineTempAloneCanPushIntoMonitor() {
        // temp only carries a 0.35 weight, so it takes a genuinely high reading on its own
        // to cross the 0.30 MONITOR threshold: (125-90)/40 * 0.35 = 0.306
        var result = scorer.score(new RiskScorer.RiskInput(125, 2.0, 10, 0));

        assertThat(result.decision()).isEqualTo(HealthDecision.MONITOR);
    }

    @Test
    void veryHighEverythingIsServiceNow() {
        var result = scorer.score(new RiskScorer.RiskInput(130, 8, 100, 3));

        assertThat(result.riskScore()).isEqualTo(1.0);
        assertThat(result.decision()).isEqualTo(HealthDecision.SERVICE_NOW);
    }

    @Test
    void singleFaultCodeAloneIsNotEnoughForServiceNow() {
        // one fault code contributes 1/3 * 0.25 weight = ~0.083 to the score, nowhere near 0.7
        var result = scorer.score(new RiskScorer.RiskInput(90, 2.0, 10, 1));

        assertThat(result.decision()).isNotEqualTo(HealthDecision.SERVICE_NOW);
    }

    @Test
    void scoreNeverGoesBelowZeroForReadingsBetterThanBaseline() {
        // temps/vibration below the "normal" baseline shouldn't produce a negative score
        var result = scorer.score(new RiskScorer.RiskInput(60, 0.5, 0, 0));

        assertThat(result.riskScore()).isEqualTo(0.0);
    }

    @Test
    void scoreIsMonotonicWithEngineTemperature() {
        var cooler = scorer.score(new RiskScorer.RiskInput(95, 2.0, 10, 0));
        var hotter = scorer.score(new RiskScorer.RiskInput(115, 2.0, 10, 0));

        assertThat(hotter.riskScore()).isGreaterThan(cooler.riskScore());
    }

    @Test
    void faultCodesSaturateAtThree() {
        var threeFaults = scorer.score(new RiskScorer.RiskInput(90, 2.0, 10, 3));
        var fiveFaults = scorer.score(new RiskScorer.RiskInput(90, 2.0, 10, 5));

        assertThat(threeFaults.riskScore()).isEqualTo(fiveFaults.riskScore());
    }
}
