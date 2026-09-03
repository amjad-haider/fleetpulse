package com.fleetpulse.health.scoring;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.NoSuchElementException;

/**
 * Loads the model ml-training produces and runs inference. Input/output
 * tensor names ("input" / "probabilities") were confirmed against the actual
 * exported model rather than assumed from skl2onnx's usual defaults, which
 * turned out not to match here ("probabilities", not "output_probability").
 *
 * Feature order must exactly match ml-training's generate_data.py
 * FEATURE_COLUMNS: engine_temp_c, vibration_mm_s, rpm, brake_wear_pct,
 * fault_code_count, odometer_km, avg_engine_temp_30d, temp_deviation,
 * fault_events_24h.
 */
@Component
public class OnnxRiskScorer {

    private static final String OUTPUT_PROBABILITIES = "probabilities";

    private final OrtEnvironment environment;
    private final OrtSession session;

    public OnnxRiskScorer(@Value("${fleetpulse.health.model-path}") String modelPath) {
        this.environment = OrtEnvironment.getEnvironment();
        try (InputStream modelStream = getClass().getResourceAsStream(modelPath)) {
            if (modelStream == null) {
                throw new IllegalStateException("ONNX model not found on classpath: " + modelPath);
            }
            byte[] modelBytes = modelStream.readAllBytes();
            this.session = environment.createSession(modelBytes);
        } catch (IOException | OrtException ex) {
            throw new IllegalStateException("failed to load ONNX model from " + modelPath, ex);
        }
    }

    public double score(float[] features) {
        try {
            float[][] input = {features};
            try (OnnxTensor inputTensor = OnnxTensor.createTensor(environment, input);
                 OrtSession.Result result = session.run(Collections.singletonMap(inputName(), inputTensor))) {
                float[][] probabilities = (float[][]) result.get(OUTPUT_PROBABILITIES)
                        .orElseThrow(() -> new NoSuchElementException("no '" + OUTPUT_PROBABILITIES + "' output from ONNX session"))
                        .getValue();
                return probabilities[0][1]; // P(class 1), i.e. needs-maintenance
            }
        } catch (OrtException ex) {
            throw new RuntimeException("ONNX inference failed", ex);
        }
    }

    private String inputName() {
        return session.getInputNames().iterator().next();
    }

    @PreDestroy
    void close() {
        try {
            session.close();
        } catch (OrtException ex) {
            // shutting down anyway, nothing to do about a close failure
        }
    }
}
