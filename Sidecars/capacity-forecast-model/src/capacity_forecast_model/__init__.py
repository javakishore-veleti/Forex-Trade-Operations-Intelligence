"""Forecasts capacity needs for FX trade infrastructure."""

__version__ = "0.1.0"

from .detector import CompletionTimeForecaster, ForecastResult
from .webhook import post_shortfall_alert
from .config import WEBHOOK_URL, SHORTFALL_THRESHOLD_MINUTES


def run() -> None:
    """Entry point: run the capacity forecast model loop."""
    import logging

    from .config import POLL_INTERVAL_SECONDS

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
    )
    logger = logging.getLogger(__name__)
    logger.info(
        "Starting capacity forecast model (shortfall_threshold=%d min, webhook=%s)",
        SHORTFALL_THRESHOLD_MINUTES,
        WEBHOOK_URL,
    )

    logger.info("Capacity forecast model is ready. Poll interval: %ds", POLL_INTERVAL_SECONDS)
    logger.info("Waiting for branch progress data (no-op in scaffold mode)...")


__all__ = [
    "__version__",
    "CompletionTimeForecaster",
    "ForecastResult",
    "post_shortfall_alert",
    "run",
]
