"""Structured fact extraction from log entries."""

import logging
import re
from dataclasses import dataclass, field
from datetime import datetime, timezone

from .config import MAX_FIELD_LENGTH

logger = logging.getLogger(__name__)

# Common log line patterns for FX trade operations
_TIMESTAMP_PATTERN = re.compile(
    r"(\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})?)"
)
_LEVEL_PATTERN = re.compile(r"\b(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\b")
_TRADE_ID_PATTERN = re.compile(r"(FX-\d{6})")
_CORRELATION_ID_PATTERN = re.compile(
    r"(?:correlationId|traceId|requestId)[=: ]*([0-9a-fA-F-]{8,36})"
)
_SERVICE_PATTERN = re.compile(r"\b(trade-lifecycle|state-reconciliation|risk-calculation|eod-processing|business-calendar)\b")
_EXCEPTION_PATTERN = re.compile(r"((?:[a-zA-Z_$][a-zA-Z0-9_$]*\.)*[A-Z][a-zA-Z0-9_$]*Exception)")
_KEY_VALUE_PATTERN = re.compile(r"(\w+)=([^\s,;]+)")


@dataclass
class NormalizedFact:
    """A structured fact extracted from a raw log entry."""

    timestamp: str | None = None
    level: str | None = None
    service: str | None = None
    trade_id: str | None = None
    correlation_id: str | None = None
    exception_class: str | None = None
    message: str = ""
    extracted_fields: dict[str, str] = field(default_factory=dict)
    raw_line: str = ""


class LogFactExtractor:
    """
    Extracts structured facts from raw log lines.
    Applies regex-based pattern matching to identify trade IDs,
    service names, log levels, timestamps, and key-value pairs.
    """

    def __init__(self, max_field_length: int = MAX_FIELD_LENGTH):
        self._max_len = max_field_length

    def extract(self, line: str) -> NormalizedFact:
        """
        Extract structured facts from a single log line.
        """
        fact = NormalizedFact(raw_line=line[:self._max_len])

        # Timestamp
        ts_match = _TIMESTAMP_PATTERN.search(line)
        if ts_match:
            fact.timestamp = ts_match.group(1)

        # Log level
        level_match = _LEVEL_PATTERN.search(line)
        if level_match:
            fact.level = level_match.group(1)

        # Service name
        svc_match = _SERVICE_PATTERN.search(line)
        if svc_match:
            fact.service = svc_match.group(1)

        # Trade ID
        trade_match = _TRADE_ID_PATTERN.search(line)
        if trade_match:
            fact.trade_id = trade_match.group(1)

        # Correlation ID
        corr_match = _CORRELATION_ID_PATTERN.search(line)
        if corr_match:
            fact.correlation_id = corr_match.group(1)

        # Exception class
        exc_match = _EXCEPTION_PATTERN.search(line)
        if exc_match:
            fact.exception_class = exc_match.group(1)

        # Key-value pairs
        for kv_match in _KEY_VALUE_PATTERN.finditer(line):
            key = kv_match.group(1)
            value = kv_match.group(2)[:self._max_len]
            fact.extracted_fields[key] = value

        # Message — the portion after level (simplified)
        if level_match:
            msg_start = level_match.end()
            fact.message = line[msg_start:].strip()[:self._max_len]
        else:
            fact.message = line.strip()[:self._max_len]

        return fact

    def extract_batch(self, lines: list[str]) -> list[NormalizedFact]:
        """Extract facts from a batch of log lines."""
        return [self.extract(line) for line in lines if line.strip()]
