"""Detects anomalies in FX trade KPI time-series data."""

__version__ = "0.1.0"

from .detector import RollingBaselineDetector, AnomalyResult
from .webhook import post_anomaly
from .config import DETECTION_THRESHOLD, WEBHOOK_URL


def run() -> None:
    """Entry point: run the KPI anomaly detection loop."""
    import logging
    import time

    from .config import POLL_INTERVAL_SECONDS

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
    )
    logger = logging.getLogger(__name__)
    logger.info(
        "Starting KPI anomaly detector (threshold=%.2f, webhook=%s)",
        DETECTION_THRESHOLD,
        WEBHOOK_URL,
    )

    detector = RollingBaselineDetector()

    # In production, this would poll metrics endpoints.
    # For local-deploy placeholder, it logs readiness and exits cleanly.
    logger.info("KPI anomaly detector is ready. Poll interval: %ds", POLL_INTERVAL_SECONDS)
    logger.info("Waiting for metrics data (no-op in scaffold mode)...")


__all__ = [
    "__version__",
    "RollingBaselineDetector",
    "AnomalyResult",
    "post_anomaly",
    "run",
]
