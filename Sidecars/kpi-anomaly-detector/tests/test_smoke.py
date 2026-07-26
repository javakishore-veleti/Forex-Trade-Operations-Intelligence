"""Smoke tests — verify the package is importable."""


def test_import():
    import kpi_anomaly_detector

    assert hasattr(kpi_anomaly_detector, "__version__")
    assert kpi_anomaly_detector.__version__ == "0.1.0"
