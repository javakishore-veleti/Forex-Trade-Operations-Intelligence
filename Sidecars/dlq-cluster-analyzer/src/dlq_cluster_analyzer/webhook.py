"""Webhook POST with retry logic for dlq-cluster-analyzer."""

import json
import time
import urllib.request
import urllib.error
import logging

from .config import WEBHOOK_URL

logger = logging.getLogger(__name__)

MAX_RETRIES = 3
BACKOFF_SECONDS = 2.0


def post_cluster_summary(payload: dict) -> bool:
    """
    POST cluster summary payload to the configured webhook URL.
    Retries up to 3 times with 2-second exponential backoff.

    Returns True if delivery succeeded, False otherwise.
    """
    data = json.dumps(payload).encode("utf-8")
    headers = {"Content-Type": "application/json"}

    for attempt in range(1, MAX_RETRIES + 1):
        try:
            req = urllib.request.Request(
                WEBHOOK_URL, data=data, headers=headers, method="POST"
            )
            with urllib.request.urlopen(req, timeout=10) as resp:
                if resp.status < 300:
                    logger.info("Webhook POST succeeded on attempt %d", attempt)
                    return True
        except (urllib.error.URLError, urllib.error.HTTPError, OSError) as exc:
            logger.warning(
                "Webhook POST attempt %d/%d failed: %s", attempt, MAX_RETRIES, exc
            )
            if attempt < MAX_RETRIES:
                time.sleep(BACKOFF_SECONDS * attempt)

    logger.error("Webhook POST exhausted all %d retries", MAX_RETRIES)
    return False
