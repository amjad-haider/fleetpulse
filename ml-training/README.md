# ml-training

Trains the risk-scoring model that (eventually) replaces health-engine's
rule-based fallback. There's no real fleet telemetry to train on, so this
uses synthetic data with a hand-built ground-truth rule and some label
noise, not a real historical dataset.

## Setup

Needs Python 3.11 or 3.12 — `onnxruntime`/`skl2onnx` don't have wheels for
very new Python versions yet, so a dedicated environment is worth it rather
than fighting the system interpreter:

```bash
conda create -n fleetpulse-ml python=3.12
conda activate fleetpulse-ml
pip install -r requirements.txt
```

## Run it

```bash
python generate_data.py   # writes data/telemetry_training_data.csv
python train.py           # trains, evaluates, exports models/risk_model.onnx
python -m pytest tests/   # data sanity checks + a smaller end-to-end run
```

## What it produces

- `models/risk_model.onnx` — the trained model, loadable from Java via ONNX
  Runtime once health-engine is wired up to use it
- `models/metrics.json` — evaluation results on the held-out test set

The model outputs a risk score (a probability), not a hard yes/no —
health-engine will bucket that score into OK/MONITOR/SERVICE_NOW with its
own thresholds, the same 0.3/0.7 split the existing rule-based scorer uses.
That's why ROC-AUC (threshold-independent) is the headline metric in
`metrics.json` rather than accuracy or F1 at some arbitrary cutoff.

## Feature set

Matches `HealthScoreRequest` in `fleetpulse-proto`'s `health.proto`, so the
trained model lines up with what health-engine's gRPC contract already
expects: `engine_temp_c`, `vibration_mm_s`, `rpm`, `brake_wear_pct`,
`fault_code_count`, `odometer_km`, `avg_engine_temp_30d`, `temp_deviation`,
`fault_events_24h`.

Note that health-engine doesn't actually compute the rolling features
(`avg_engine_temp_30d`, `temp_deviation`, `fault_events_24h`) yet — today's
`RiskScorer` only uses the four raw-reading fields. Wiring this model in for
real means adding that rolling aggregation first.
