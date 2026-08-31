"""
Generates synthetic vehicle telemetry with a maintenance-needed label.

Feature set matches the HealthScoreRequest contract in fleetpulse-proto's
health.proto, so the trained model can eventually be dropped straight into
health-engine's scoring path.

There's no real fleet telemetry to train on, obviously, so this is synthetic
data with a hand-built ground-truth rule plus label noise. The rule uses
feature interactions (not just single thresholds) so a tree-based model
actually has something worth learning beyond what the existing rule-based
scorer already does.
"""

import pathlib

import numpy as np
import pandas as pd

FEATURE_COLUMNS = [
    "engine_temp_c",
    "vibration_mm_s",
    "rpm",
    "brake_wear_pct",
    "fault_code_count",
    "odometer_km",
    "avg_engine_temp_30d",
    "temp_deviation",
    "fault_events_24h",
]

LABEL_COLUMN = "needs_maintenance"


def generate(n_rows: int, seed: int = 42) -> pd.DataFrame:
    rng = np.random.default_rng(seed)

    odometer_km = rng.uniform(500, 300_000, n_rows)
    # older vehicles run a bit hotter and vibrate a bit more on average
    age_factor = odometer_km / 300_000

    avg_engine_temp_30d = rng.normal(88 + 6 * age_factor, 3, n_rows)
    engine_temp_c = avg_engine_temp_30d + rng.normal(0, 4, n_rows) + rng.exponential(2, n_rows) * (rng.random(n_rows) < 0.08)
    temp_deviation = engine_temp_c - avg_engine_temp_30d

    vibration_mm_s = np.clip(rng.normal(1.8 + 1.5 * age_factor, 0.6, n_rows), 0, None)
    rpm = rng.normal(1450, 200, n_rows).clip(600, 3000)
    brake_wear_pct = np.clip(rng.normal(15 + 60 * age_factor, 15, n_rows), 0, 100)

    fault_code_count = rng.poisson(0.15 + 0.6 * age_factor, n_rows)
    fault_events_24h = rng.poisson(0.1 + 0.5 * age_factor, n_rows)

    df = pd.DataFrame({
        "engine_temp_c": engine_temp_c,
        "vibration_mm_s": vibration_mm_s,
        "rpm": rpm,
        "brake_wear_pct": brake_wear_pct,
        "fault_code_count": fault_code_count,
        "odometer_km": odometer_km,
        "avg_engine_temp_30d": avg_engine_temp_30d,
        "temp_deviation": temp_deviation,
        "fault_events_24h": fault_events_24h,
    })

    df[LABEL_COLUMN] = label(df, rng)
    return df


def label(df: pd.DataFrame, rng: np.random.Generator) -> np.ndarray:
    # feature interactions, not single thresholds, so the model has to learn
    # something the existing rule-based scorer (health-engine's RiskScorer)
    # doesn't already capture
    at_risk = (
        ((df["temp_deviation"] > 12) & (df["fault_events_24h"] >= 1))
        | (df["brake_wear_pct"] > 85)
        | ((df["vibration_mm_s"] > 4.5) & (df["odometer_km"] > 150_000))
        | (df["fault_code_count"] >= 3)
    )

    # 4% label noise, real maintenance records are never perfectly clean
    noise = rng.random(len(df)) < 0.04
    return (at_risk ^ noise).astype(int)


if __name__ == "__main__":
    dataset = generate(n_rows=15_000)
    output_path = pathlib.Path("data/telemetry_training_data.csv")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    dataset.to_csv(output_path, index=False)
    print(f"wrote {len(dataset)} rows, {dataset[LABEL_COLUMN].mean():.1%} positive")
