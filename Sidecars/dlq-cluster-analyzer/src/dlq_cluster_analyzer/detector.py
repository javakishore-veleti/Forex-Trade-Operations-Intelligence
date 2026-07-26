"""Stack-trace clustering logic for DLQ error messages."""

import hashlib
import logging
import re
from dataclasses import dataclass, field
from datetime import datetime, timezone

from .config import MIN_CLUSTER_SIZE, SIMILARITY_THRESHOLD

logger = logging.getLogger(__name__)


@dataclass
class ErrorCluster:
    """A cluster of similar DLQ errors."""

    cluster_id: str
    representative_trace: str
    count: int
    first_seen: str
    last_seen: str
    sample_trade_ids: list[str] = field(default_factory=list)


class StackTraceClusterer:
    """
    Groups DLQ error entries by stack-trace similarity.
    Uses normalized stack-trace fingerprinting for clustering.
    """

    def __init__(
        self,
        similarity_threshold: float = SIMILARITY_THRESHOLD,
        min_cluster_size: int = MIN_CLUSTER_SIZE,
    ):
        self._similarity_threshold = similarity_threshold
        self._min_cluster_size = min_cluster_size
        self._clusters: dict[str, dict] = {}

    @staticmethod
    def _normalize_trace(trace: str) -> str:
        """
        Normalize a stack trace by removing line numbers, memory addresses,
        and variable identifiers to produce a stable fingerprint.
        """
        # Remove line numbers
        normalized = re.sub(r":\d+", ":N", trace)
        # Remove hex addresses
        normalized = re.sub(r"0x[0-9a-fA-F]+", "0xADDR", normalized)
        # Remove UUIDs
        normalized = re.sub(
            r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
            "UUID",
            normalized,
        )
        # Remove FX-prefixed IDs
        normalized = re.sub(r"FX-\d+", "FX-ID", normalized)
        return normalized

    @staticmethod
    def _fingerprint(normalized_trace: str) -> str:
        """Generate a SHA-256 fingerprint of the normalized trace."""
        return hashlib.sha256(normalized_trace.encode()).hexdigest()[:16]

    def ingest(self, trace: str, trade_id: str = "") -> str:
        """
        Ingest a DLQ error trace and assign it to a cluster.
        Returns the cluster_id.
        """
        normalized = self._normalize_trace(trace)
        fp = self._fingerprint(normalized)
        now = datetime.now(timezone.utc).isoformat()

        if fp not in self._clusters:
            self._clusters[fp] = {
                "representative_trace": trace[:500],
                "count": 0,
                "first_seen": now,
                "last_seen": now,
                "sample_trade_ids": [],
            }

        cluster = self._clusters[fp]
        cluster["count"] += 1
        cluster["last_seen"] = now
        if trade_id and len(cluster["sample_trade_ids"]) < 5:
            cluster["sample_trade_ids"].append(trade_id)

        return fp

    def get_significant_clusters(self) -> list[ErrorCluster]:
        """Return clusters that meet the minimum size threshold."""
        results = []
        for cluster_id, data in self._clusters.items():
            if data["count"] >= self._min_cluster_size:
                results.append(
                    ErrorCluster(
                        cluster_id=cluster_id,
                        representative_trace=data["representative_trace"],
                        count=data["count"],
                        first_seen=data["first_seen"],
                        last_seen=data["last_seen"],
                        sample_trade_ids=data["sample_trade_ids"],
                    )
                )
        return sorted(results, key=lambda c: c.count, reverse=True)

    def reset(self) -> None:
        """Clear all clusters."""
        self._clusters.clear()
