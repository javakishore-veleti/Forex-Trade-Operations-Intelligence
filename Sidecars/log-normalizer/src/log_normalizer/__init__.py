"""Log Normalizer — structures FX trade operation logs for analysis."""

__version__ = "0.1.0"

from .detector import LogFactExtractor, NormalizedFact
from .webhook import post_normalized_facts
from .config import WEBHOOK_URL, LOG_SOURCE


def run() -> None:
    """Entry point: run the log normalizer."""
    import logging

    from .config import BATCH_SIZE

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
    )
    logger = logging.getLogger(__name__)
    logger.info(
        "Starting log normalizer (source=%s, webhook=%s)",
        LOG_SOURCE,
        WEBHOOK_URL,
    )

    logger.info("Log normalizer is ready. Batch size: %d", BATCH_SIZE)
    logger.info("Waiting for log input (no-op in scaffold mode)...")


__all__ = [
    "__version__",
    "LogFactExtractor",
    "NormalizedFact",
    "post_normalized_facts",
    "run",
]
