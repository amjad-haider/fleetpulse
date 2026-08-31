import sys
import pathlib

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))

from generate_data import generate
from train import export_to_onnx, train


def test_training_pipeline_produces_a_model_better_than_random(tmp_path):
    df = generate(n_rows=4000, seed=123)

    model, metrics, _ = train(df)

    # a model no better than a coin flip would mean the features carry no signal
    assert metrics["roc_auc"] > 0.65
    assert metrics["train_rows"] + metrics["test_rows"] == len(df)


def test_onnx_export_round_trips_and_passes_parity_check(tmp_path, monkeypatch):
    import train as train_module

    monkeypatch.setattr(train_module, "MODEL_PATH", tmp_path / "model.onnx")

    df = generate(n_rows=3000, seed=7)
    model, _, X_test = train(df)

    # export_to_onnx raises if the ONNX output diverges from sklearn's own
    # predictions, so simply not raising here is the assertion
    export_to_onnx(model, X_test)

    assert (tmp_path / "model.onnx").exists()
