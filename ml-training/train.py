"""
Trains a gradient-boosted classifier on the synthetic telemetry data and
exports it to ONNX for health-engine to load via ONNX Runtime.

Usage:
    python generate_data.py   # writes data/telemetry_training_data.csv
    python train.py
"""

import json
import pathlib

import numpy as np
import onnxruntime as rt
import pandas as pd
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.metrics import (
    accuracy_score,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import train_test_split
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

from generate_data import FEATURE_COLUMNS, LABEL_COLUMN

DATA_PATH = pathlib.Path("data/telemetry_training_data.csv")
MODEL_PATH = pathlib.Path("models/risk_model.onnx")
METRICS_PATH = pathlib.Path("models/metrics.json")


def load_data() -> pd.DataFrame:
    if not DATA_PATH.exists():
        raise FileNotFoundError(f"{DATA_PATH} not found, run generate_data.py first")
    return pd.read_csv(DATA_PATH)


def train(df: pd.DataFrame) -> tuple[GradientBoostingClassifier, dict, np.ndarray]:
    X = df[FEATURE_COLUMNS].to_numpy(dtype=np.float32)
    y = df[LABEL_COLUMN].to_numpy()

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    model = GradientBoostingClassifier(
        n_estimators=150,
        max_depth=3,
        learning_rate=0.1,
        random_state=42,
    )
    model.fit(X_train, y_train)

    probabilities = model.predict_proba(X_test)[:, 1]

    # this model outputs a risk score, not a hard yes/no — health-engine buckets
    # it into OK/MONITOR/SERVICE_NOW with its own thresholds (0.3 / 0.7, same as
    # the existing rule-based scorer), so precision/recall at the default 0.5 cut
    # isn't actually the operative number. ROC-AUC (threshold-independent ranking
    # quality) is reported as the headline metric; 0.5-cut numbers are kept too,
    # only as a reference point.
    predictions_at_default_cut = model.predict(X_test)
    metrics = {
        "roc_auc": roc_auc_score(y_test, probabilities),
        "accuracy_at_0.5": accuracy_score(y_test, predictions_at_default_cut),
        "precision_at_0.5": precision_score(y_test, predictions_at_default_cut),
        "recall_at_0.5": recall_score(y_test, predictions_at_default_cut),
        "f1_at_0.5": f1_score(y_test, predictions_at_default_cut),
        "train_rows": len(X_train),
        "test_rows": len(X_test),
        "positive_rate": float(y.mean()),
    }
    return model, metrics, X_test


def export_to_onnx(model: GradientBoostingClassifier, X_test: np.ndarray) -> None:
    initial_type = [("input", FloatTensorType([None, len(FEATURE_COLUMNS)]))]
    # skl2onnx defaults a classifier's probability output to a ZipMap (a list
    # of {class_label: probability} dicts), which is convenient in Python but
    # a pain to consume from Java's ONNX Runtime bindings. This model only
    # ever gets served from health-engine (Java), never from Python, so
    # zipmap=False turns that output into a plain float tensor instead:
    # much simpler on the consuming side, for zero cost on this one.
    options = {id(model): {"zipmap": False}}
    onnx_model = convert_sklearn(model, initial_types=initial_type, options=options, target_opset=17)

    MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
    with open(MODEL_PATH, "wb") as f:
        f.write(onnx_model.SerializeToString())

    verify_onnx_matches_sklearn(model, X_test)


def verify_onnx_matches_sklearn(model: GradientBoostingClassifier, X_test: np.ndarray) -> None:
    """skl2onnx conversion can silently drift from the original model's
    behavior, so check a sample of predictions actually agree before
    trusting the exported file."""
    session = rt.InferenceSession(str(MODEL_PATH), providers=["CPUExecutionProvider"])
    input_name = session.get_inputs()[0].name

    sample = X_test[:200]
    sklearn_probs = model.predict_proba(sample)[:, 1]

    # with zipmap=False, output[1] is a plain (N, 2) float array: column 0 is
    # P(class 0), column 1 is P(class 1) - the risk score health-engine wants
    onnx_output = session.run(None, {input_name: sample})
    onnx_probs = onnx_output[1][:, 1]

    max_diff = np.max(np.abs(sklearn_probs - onnx_probs))
    if max_diff > 1e-4:
        raise RuntimeError(f"ONNX export diverged from sklearn model: max diff {max_diff}")
    print(f"ONNX parity check passed, max probability diff: {max_diff:.6f}")


if __name__ == "__main__":
    data = load_data()
    trained_model, run_metrics, held_out_X = train(data)

    print("evaluation on held-out test set:")
    for key, value in run_metrics.items():
        print(f"  {key}: {value}")

    export_to_onnx(trained_model, held_out_X)

    METRICS_PATH.parent.mkdir(parents=True, exist_ok=True)
    with open(METRICS_PATH, "w") as f:
        json.dump(run_metrics, f, indent=2)

    print(f"model written to {MODEL_PATH}")
