"""Completion-time forecast vs cutoff detection logic."""

import logging
import math
from dataclasses import dataclass, field
from datetime import datetime, timezone

from .config import CONFIDENCE_LEVEL, LOOKBACK_PERIODS, SHORTFALL_THRESHOLD_MINUTES

logger = logging.getLogger(__name__)


@dataclass
class ForecastResult:
    """Result of capacity forecast for a processing branch."""

    branch_id: str
    estimated_completion_minutes: float
    cutoff_minutes_remaining: float
    shortfall_minutes: float
    confidence: float
    is_at_risk: bool
    timestamp: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())


class CompletionTimeForecaster:
    """
    Forecasts whether a processing branch will complete before its cutoff
    based on historical completion rates.
    Uses simple linear extrapolation with confidence intervals.
    """

    def __init__(
        self,
        shortfall_threshold_minutes: int = SHORTFALL_THRESHOLD_MINUTES,
        confidence_level: float = CONFIDENCE_LEVEL,
        lookback_periods: int = LOOKBACK_PERIODS,
    ):
        self._shortfall_threshold = shortfall_threshold_minutes
        self._confidence_level = confidence_level
        self._lookback_periods = lookback_periods
        self._history: dict[str, list[float]] = {}

    def record_progress(self, branch_id: str, completion_rate: float) -> None:
        """
        Record a progress sample (completion_rate as fraction 0.0–1.0).
        """
        buf = self._history.setdefault(branch_id, [])
        buf.append(completion_rate)
        if len(buf) > self._lookback_periods:
            buf.pop(0)

    def forecast(
        self, branch_id: str, cutoff_minutes_remaining: float
    ) -> ForecastResult | None:
        """
        Forecast whether the branch will complete before the cutoff.
        Returns None if insufficient history.
        """
        history = self._history.get(branch_id, [])
        if len(history) < 2:
            return None

        # Calculate rate of progress per period
        rates = [history[i] - history[i - 1] for i in range(1, len(history))]
        if not rates:
            return None

        avg_rate = sum(rates) / len(rates)
        current_completion = history[-1]
        remaining = 1.0 - current_completion

        if avg_rate <= 0:
            # No progress or negative — at risk
            estimated_minutes = float("inf")
        else:
            periods_needed = remaining / avg_rate
            # Each period is approximately POLL_INTERVAL worth of time
            # Estimate: minutes = periods_needed * poll_interval_equivalent
            estimated_minutes = periods_needed * (cutoff_minutes_remaining / max(len(history), 1))

        shortfall = estimated_minutes - cutoff_minutes_remaining

        # Confidence based on data points available
        confidence = min(len(history) / self._lookback_periods, 1.0) * self._confidence_level

        is_at_risk = shortfall > self._shortfall_threshold and confidence >= self._confidence_level * 0.5

        result = ForecastResult(
            branch_id=branch_id,
            estimated_completion_minutes=estimated_minutes if math.isfinite(estimated_minutes) else 9999.0,
            cutoff_minutes_remaining=cutoff_minutes_remaining,
            shortfall_minutes=max(shortfall, 0.0) if math.isfinite(shortfall) else 9999.0,
            confidence=confidence,
            is_at_risk=is_at_risk,
        )

        if is_at_risk:
            logger.warning(
                "Capacity shortfall for branch %s: estimated=%.1f min, cutoff=%.1f min, shortfall=%.1f min",
                branch_id,
                result.estimated_completion_minutes,
                cutoff_minutes_remaining,
                result.shortfall_minutes,
            )

        return result

    def reset(self, branch_id: str | None = None) -> None:
        """Reset history for a specific branch or all branches."""
        if branch_id:
            self._history.pop(branch_id, None)
        else:
            self._history.clear()
