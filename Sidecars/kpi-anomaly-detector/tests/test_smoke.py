"""Tests for kpi-anomaly-detector detection logic."""

import math


def test_import():
    import kpi_anomaly_detector

    assert hasattr(kpi_anomaly_detector, "__version__")
    assert kpi_anomaly_detector.__version__ == "0.1.0"


def test_no_anomaly_below_threshold():
    from kpi_anomaly_detector.detector import RollingBaselineDetector

    detector = RollingBaselineDetector(window_size=20, threshold=2.5, min_data_points=5)

    # Ingest stable values
    for i in range(10):
        result = detector.ingest("trade_count", 100.0 + (i % 2))

    # Last result should not be anomaly
    assert result is not None
    assert result.is_anomaly is False


def test_anomaly_detected_on_spike():
    from kpi_anomaly_detector.detector import RollingBaselineDetector

    detector = RollingBaselineDetector(window_size=20, threshold=2.0, min_data_points=5)

    # Build a stable baseline
    for _ in range(15):
        detector.ingest("latency_ms", 50.0)

    # Inject a spike
    result = detector.ingest("latency_ms", 200.0)

    assert result is not None
    assert result.is_anomaly is True
    assert result.z_score > 2.0


def test_insufficient_data_returns_none():
    from kpi_anomaly_detector.detector import RollingBaselineDetector

    detector = RollingBaselineDetector(window_size=20, threshold=2.5, min_data_points=10)

    # Only 5 data points — below min_data_points
    for i in range(5):
        result = detector.ingest("volume", float(i))

    assert result is None


def test_reset_clears_buffer():
    from kpi_anomaly_detector.detector import RollingBaselineDetector

    detector = RollingBaselineDetector(window_size=10, threshold=2.0, min_data_points=3)

    for _ in range(5):
        detector.ingest("metric_a", 10.0)

    detector.reset("metric_a")

    # After reset, should return None (not enough data)
    result = detector.ingest("metric_a", 10.0)
    assert result is None
