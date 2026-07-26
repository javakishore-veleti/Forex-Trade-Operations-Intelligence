# ADR-0022: Structured JSON Logs vs Text Logs vs Binary Logging

## Status
Accepted

## Context
Seven microservices, Kafka Streams, and Python sidecars all produce operational logs. These logs
must be:
- Searchable by `traceId`, `tradeId`, `service`, and severity in Elasticsearch
- Correlatable with distributed traces (same `traceId` across log and span)
- Parseable without fragile regex patterns
- Compact enough for 30-day retention at ~10K log lines/minute aggregate

The ELK stack (Elasticsearch, Logstash/Filebeat, Kibana) is the target log aggregation platform.

## Decision
All services emit **structured JSON logs** to stdout, one JSON object per line (JSON Lines format).

```json
{
  "timestamp": "2025-03-14T16:22:01.123Z",
  "level": "INFO",
  "service": "trade-lifecycle-service",
  "traceId": "abc123def456",
  "spanId": "789ghi",
  "tradeId": "FX-000042",
  "message": "State transition applied",
  "context": {"from": "VALIDATED", "to": "ENRICHED", "durationMs": 12}
}
```

Implementation:
- Java services: Logback with `logstash-logback-encoder` (JSON layout, OTel MDC injection)
- Python sidecars: `structlog` with JSON renderer
- Filebeat ships JSON directly to Elasticsearch — no parsing/grok needed
- MDC populated by OTel agent: `traceId`, `spanId` injected automatically

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| **Plain text logs with Logstash grok parsing** | Fragile regex patterns break on message format changes; multi-line exceptions need special handling; CPU-intensive parsing at ingest |
| **Binary logging (protobuf/flatbuffers)** | Not human-readable in terminals during local development; requires custom tooling for ad-hoc debugging; overkill for our log volume |
| **Semi-structured (logfmt key=value)** | Better than text but less universal tooling support than JSON; no nested objects for rich context; Elasticsearch native JSON support wasted |
| **Log to files + rotation** | Container anti-pattern; stdout is the 12-factor standard; file-based adds volume mount complexity |

## Consequences

### Positive
- Zero-parsing ingest: Filebeat sends JSON directly to Elasticsearch index
- Any field is searchable: `tradeId:FX-000042 AND level:ERROR` works immediately
- Trace correlation automatic: search by `traceId` finds all log lines in a distributed trace
- `context` object allows rich structured data without polluting the message string
- Same format across Java and Python — unified Kibana dashboards

### Negative
- JSON is more verbose than text (~30% larger per line) — increases storage cost
- Human readability in raw terminal output is worse than formatted text
- Developers must configure JSON logging locally (or use a profile switch)

### Mitigations
- Local development profile (`spring.profiles.active=local`) uses human-readable console format
- Elasticsearch compression (LZ4) reduces storage impact to ~10% overhead vs text
- Saved Kibana queries documented in `docs/observability/structured-logging.md` (4 queries)
- Log volume controlled by level: `INFO` in production, `DEBUG` only in local/dev profiles
