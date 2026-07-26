"""Clusters and categorizes dead-letter-queue errors for FX trade operations."""

__version__ = "0.1.0"

from .detector import StackTraceClusterer, ErrorCluster
from .webhook import post_cluster_summary
from .config import WEBHOOK_URL, SIMILARITY_THRESHOLD


def run() -> None:
    """Entry point: run the DLQ cluster analysis loop."""
    import logging

    from .config import POLL_INTERVAL_SECONDS

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
    )
    logger = logging.getLogger(__name__)
    logger.info(
        "Starting DLQ cluster analyzer (similarity=%.2f, webhook=%s)",
        SIMILARITY_THRESHOLD,
        WEBHOOK_URL,
    )

    logger.info("DLQ cluster analyzer is ready. Poll interval: %ds", POLL_INTERVAL_SECONDS)
    logger.info("Waiting for DLQ data (no-op in scaffold mode)...")


__all__ = [
    "__version__",
    "StackTraceClusterer",
    "ErrorCluster",
    "post_cluster_summary",
    "run",
]
