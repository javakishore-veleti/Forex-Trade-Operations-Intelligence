# ADR-0017: DLQ with Quarantine Headers vs Separate Error Topics

## Status
Accepted

## Context
When a consumer fails to process an event (deserialization error, business rule violation,
transient infrastructure failure), the event must not be lost and must not block the partition.
The platform needs:

- Preservation of the original event payload for replay
- Classification of failure reason (for triage prioritization)
- Correlation back to the source topic and partition
- A single place for the DLQ Triage Agent to monitor

Trades like `FX-000456` may fail enrichment due to a missing counterparty — this must be
distinguishable from a serialization error on `FX-000789`.

## Decision
Route all failed events to a **single DLQ topic** (`fxops.system.dlq`) with **quarantine headers**
that classify the failure.

Required headers on every DLQ message:
```
x-dlq-origin-topic: fxops.trade.lifecycle
x-dlq-origin-partition: 7
x-dlq-origin-offset: 142857
x-dlq-failure-reason: ENRICHMENT_MISSING_COUNTERPARTY
x-dlq-failure-category: BUSINESS_RULE
x-dlq-failure-timestamp: 2025-03-14T16:22:01Z
x-dlq-retry-count: 3
x-dlq-service: trade-lifecycle-service
```

Categories: `DESERIALIZATION`, `BUSINESS_RULE`, `TRANSIENT_INFRA`, `UNKNOWN`.

A `DlqProducer` utility in `shared-domain-contracts` standardizes header population across all
services.

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| **Separate error topic per failure type** (`fxops.dlq.deser`, `fxops.dlq.business`, etc.) | Multiplies topics to monitor; triage agent must subscribe to N topics; new failure categories require new topics + consumer config |
| **Separate DLQ per origin topic** (`fxops.trade.lifecycle.dlq`) | Better isolation but fragments the triage view; agent must monitor 6+ DLQ topics; header approach gives same info in one stream |
| **Inline retry with exponential backoff only** | Blocks partition processing during retries; does not preserve permanently-failed events; no human visibility |
| **Error database table** | Breaks the event-driven pattern; adds DB coupling to every consumer; harder to replay (must re-publish to Kafka anyway) |

## Consequences

### Positive
- Single topic for DLQ Triage Agent to monitor — simple consumer subscription
- Headers enable filtering without deserializing the payload (agent can prioritize by category)
- Original payload preserved exactly — replay is a matter of re-publishing to the origin topic
- Standardized `DlqProducer` utility ensures consistent header format across all services
- Supports automated retry (re-publish events where `failure-category = TRANSIENT_INFRA`)

### Negative
- Single topic means all failure types share partition throughput (acceptable at DLQ volumes)
- Header-based filtering requires consumer-side logic (no server-side topic filtering in Kafka)
- High-volume failure bursts (e.g., schema-breaking deployment) flood one topic

### Mitigations
- DLQ topic has 6 partitions (over-provisioned for typical DLQ volume)
- Alert fires when DLQ ingestion rate exceeds 100 messages/minute (indicates systemic failure)
- Grafana dashboard breaks down DLQ by `failure-category` header for operator visibility
