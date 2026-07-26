# ADR-0025: Azure Event Hubs Kafka Protocol over Native Protocol

**Status:** Accepted

**Date:** 2024-02-20

## Context

The platform's event-driven architecture is built on Apache Kafka APIs (producers, consumers, Kafka Streams). For Azure deployment, we need a messaging backbone compatible with existing Kafka client code without rewriting producers and consumers.

Two options were evaluated:

1. **Event Hubs with Kafka protocol** — Azure's managed event streaming service exposing a Kafka-compatible endpoint.
2. **Native Event Hubs protocol** — Azure's proprietary AMQP-based SDK.

## Decision

We adopt **Azure Event Hubs Premium tier with Kafka protocol enabled** for Azure deployment.

### Implementation

| Configuration | Value |
|--------------|-------|
| Tier | Premium (1 Processing Unit, auto-inflate to 4) |
| Protocol | Kafka (port 9093, SASL_SSL) |
| Kafka API version | Compatible with Kafka 3.x clients |
| Namespaces | `fx-platform-events` (production topics) |
| Partitions per event hub | Matches Kafka topic layout (3-12 per topic) |
| Retention | 7 days (90 days for audit topics) |

### Connection Configuration

```yaml
spring:
  kafka:
    bootstrap-servers: fx-platform-events.servicebus.windows.net:9093
    properties:
      security.protocol: SASL_SSL
      sasl.mechanism: PLAIN
      sasl.jaas.config: >
        org.apache.kafka.common.security.plain.PlainLoginModule required
        username="$ConnectionString"
        password="Endpoint=sb://fx-platform-events.servicebus.windows.net/;SharedAccessKeyName=...";
```

### Kafka Streams Consideration

The `event-sequence-processor` (Kafka Streams) has limited compatibility with Event Hubs:
- Consumer group management: ✅ supported
- Exactly-once semantics: ❌ not supported (use idempotency cache as compensating control)
- Changelog topics: ✅ supported (auto-create enabled for internal topics)

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Native Event Hubs protocol (AMQP) | Requires rewriting all producers/consumers; two separate codebases for AWS vs Azure |
| Self-managed Kafka on AKS (Strimzi) | Same operational burden as AWS alternative; defeats purpose of managed Azure deployment |
| Confluent Cloud on Azure | Additional vendor dependency and cost; Event Hubs Kafka mode sufficient for workload |

## Consequences

### Positive
- Zero code changes to producers/consumers — same Spring Kafka configuration with different bootstrap servers
- Managed service: no broker operations, automatic scaling within tier
- Integration with Azure Monitor for metrics and diagnostics
- Single codebase deploys to both AWS (MSK) and Azure (Event Hubs)

### Negative
- Not 100% Kafka-compatible: no transactions, no exactly-once, some admin APIs missing
- Partition count is fixed at creation (no dynamic increase without recreation)
- Higher per-message cost than MSK for equivalent throughput
- Kafka Streams state stores require workarounds (no compact topics)

### Mitigations
- Idempotency cache (Redis) compensates for missing exactly-once semantics
- Partition counts pre-calculated for 2-year growth projection
- Kafka Streams changelog topics configured with explicit retention (not compaction)
- Integration tests validate Event Hubs compatibility in CI
