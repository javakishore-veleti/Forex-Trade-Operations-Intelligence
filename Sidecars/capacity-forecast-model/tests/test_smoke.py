"""Tests for capacity-forecast-model forecast logic."""


def test_import():
    import capacity_forecast_model

    assert hasattr(capacity_forecast_model, "__version__")
    assert capacity_forecast_model.__version__ == "0.1.0"


def test_insufficient_history_returns_none():
    from capacity_forecast_model.detector import CompletionTimeForecaster

    forecaster = CompletionTimeForecaster()

    # Only one data point
    forecaster.record_progress("branch-APAC", 0.2)
    result = forecaster.forecast("branch-APAC", cutoff_minutes_remaining=60.0)

    assert result is None


def test_on_track_branch_not_at_risk():
    from capacity_forecast_model.detector import CompletionTimeForecaster

    forecaster = CompletionTimeForecaster(shortfall_threshold_minutes=30)

    # Simulate steady progress toward completion
    for i in range(5):
        forecaster.record_progress("branch-EMEA", (i + 1) * 0.2)

    # Already at 100% — should not be at risk
    result = forecaster.forecast("branch-EMEA", cutoff_minutes_remaining=60.0)

    assert result is not None
    assert result.is_at_risk is False


def test_stalled_branch_at_risk():
    from capacity_forecast_model.detector import CompletionTimeForecaster

    forecaster = CompletionTimeForecaster(
        shortfall_threshold_minutes=10, confidence_level=0.5, lookback_periods=5
    )

    # No progress — stalled at 10%
    for _ in range(5):
        forecaster.record_progress("branch-US", 0.1)

    result = forecaster.forecast("branch-US", cutoff_minutes_remaining=30.0)

    assert result is not None
    assert result.is_at_risk is True
    assert result.shortfall_minutes > 0


def test_reset_clears_history():
    from capacity_forecast_model.detector import CompletionTimeForecaster

    forecaster = CompletionTimeForecaster()

    forecaster.record_progress("branch-X", 0.5)
    forecaster.record_progress("branch-X", 0.6)
    forecaster.reset("branch-X")

    result = forecaster.forecast("branch-X", cutoff_minutes_remaining=60.0)
    assert result is None
