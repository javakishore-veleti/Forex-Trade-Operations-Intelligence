"""Rolling baseline + deviation detection for KPI anomalies."""

import logging
import math
from dataclasses import dataclass, field
from datetime import datetime, timezone

from .config import DETECTION_THRESHOLD, MIN_DATA_POINTS, ROLLING_WINDOW_SIZE

logger = logging.getLogger(__name__)


@dataclass
class AnomalyResult:
    """Result of anomaly detection on a single KPI data point."""

    kpi_name: str
    value: float
    mean: float
    std_dev: float
    z_score: float
    is_anomaly: bool
    timestamp: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())


class RollingBaselineDetector:
    """
    Maintains a rolling window of KPI values and detects anomalies
    using z-score deviation from the rolling mean.
    """

    def __init__(
        self,
        window_size: int = ROLLING_WINDOW_SIZE,
        threshold: float = DETECTION_THRESHOLD,
        min_data_points: int = MIN_DATA_POINTS,
    ):
        self._window_size = window_size
        self._threshold = threshold
        self._min_data_points = min_data_points
        self._buffers: dict[str, list[float]] = {}

    def ingest(self, kpi_name: str, value: float) -> AnomalyResult | None:
        """
        Ingest a new KPI value and return an AnomalyResult if an anomaly is detected.
        Returns None if not enough data points have been collected.
        """
        buf = self._buffers.setdefault(kpi_name, [])
        buf.append(value)

        # Keep only the rolling window
        if len(buf) > self._window_size:
            buf.pop(0)

        # Need minimum data points before detecting
        if len(buf) < self._min_data_points:
            return None

        mean = sum(buf) / len(buf)
        variance = sum((x - mean) ** 2 for x in buf) / len(buf)
        std_dev = math.sqrt(variance) if variance > 0 else 0.0

        # Avoid division by zero
        if std_dev == 0:
            z_score = 0.0
        else:
            z_score = (value - mean) / std_dev

        is_anomaly = abs(z_score) > self._threshold

        if is_anomaly:
            logger.info(
                "Anomaly detected for %s: value=%.4f, mean=%.4f, z_score=%.2f",
                kpi_name,
                value,
                mean,
                z_score,
            )

        return AnomalyResult(
            kpi_name=kpi_name,
            value=value,
            mean=mean,
            std_dev=std_dev,
            z_score=z_score,
            is_anomaly=is_anomaly,
        )

    def reset(self, kpi_name: str | None = None) -> None:
        """Reset the buffer for a specific KPI or all KPIs."""
        if kpi_name:
            self._buffers.pop(kpi_name, None)
        else:
            self._buffers.clear()
