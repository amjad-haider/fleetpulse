import sys
import pathlib

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))

from generate_data import FEATURE_COLUMNS, LABEL_COLUMN, generate


def test_has_all_expected_columns():
    df = generate(n_rows=500)
    for column in FEATURE_COLUMNS + [LABEL_COLUMN]:
        assert column in df.columns


def test_generation_is_deterministic_for_a_given_seed():
    first = generate(n_rows=200, seed=7)
    second = generate(n_rows=200, seed=7)
    assert first.equals(second)


def test_different_seeds_produce_different_data():
    first = generate(n_rows=200, seed=1)
    second = generate(n_rows=200, seed=2)
    assert not first.equals(second)


def test_feature_ranges_are_physically_sane():
    df = generate(n_rows=5000)
    assert (df["odometer_km"] >= 0).all()
    assert (df["vibration_mm_s"] >= 0).all()
    assert (df["rpm"] > 0).all()
    assert (df["brake_wear_pct"] >= 0).all() and (df["brake_wear_pct"] <= 100).all()
    assert (df["fault_code_count"] >= 0).all()
    assert (df["fault_events_24h"] >= 0).all()


def test_label_is_binary():
    df = generate(n_rows=1000)
    assert set(df[LABEL_COLUMN].unique()).issubset({0, 1})


def test_label_is_not_degenerate():
    # a dataset that's all-one-class would make the whole training step pointless
    df = generate(n_rows=5000)
    positive_rate = df[LABEL_COLUMN].mean()
    assert 0.01 < positive_rate < 0.5


def test_older_vehicles_trend_toward_higher_risk_features():
    df = generate(n_rows=8000)
    older = df[df["odometer_km"] > 250_000]
    newer = df[df["odometer_km"] < 50_000]
    assert older["brake_wear_pct"].mean() > newer["brake_wear_pct"].mean()
    assert older[LABEL_COLUMN].mean() > newer[LABEL_COLUMN].mean()
