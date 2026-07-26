"""Smoke tests — verify the package is importable."""


def test_import():
    import capacity_forecast_model

    assert hasattr(capacity_forecast_model, "__version__")
    assert capacity_forecast_model.__version__ == "0.1.0"
