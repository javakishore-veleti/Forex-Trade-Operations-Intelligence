# ADR-0015: Kafka Topic per Domain Aggregate

## Status
Accepted

## Context
The platform publishes 25+ domain event types across 7 microservices. Events must be:
- Ordered per trade (consumers process `FX-000042` events in sequence)
- Independently scalable (trade-lifecycle events are 10× higher volume than EOD events)
- Consumable by focused consumers (the DLQ triage agent should not parse settlement events)

The topic design determines partition strategy, consumer group assignment, schema evolution scope,
and operational complexity.

## Decision
One **Kafka topic per domain aggregate**, with naming convention `fxops.{domain}.{aggregate}`:

| Topic | Aggregate | Partition Key |
|-------|-----------|---------------|
| `fxops.trade.lifecycle` | Trade lifecycle state changes | `tradeId` |
| `fxops.trade.risk` | Risk calculation results | `tradeId` |
| `fxops.trade.settlement` | Settlement instructions | `tradeId` |
| `fxops.eod.processing` | EOD batch events | `businessDate` |
| `fxops.reconciliation.state` | Reconciliation findings | `tradeId` |
| `fxops.system.dlq` | Dead-letter entries | `originalTopic+tradeId` |

Key properties:
- Partition key = aggregate ID guarantees per-aggregate ordering
- Each topic has independent partition count tuned to its throughput
- Schema Registry subjects are per-topic — evolution is scoped to one aggregate

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| **Single monolithic topic (`fxops.all-events`)** | Forces every consumer to filter irrelevant events; partition key conflict between domains; schema evolution affects all producers |
| **Topic per event type (`fxops.trade.validation-passed`, `fxops.trade.enrichment-complete`)** | Explosion of 25+ topics; consumers needing full trade history must subscribe to many topics; ordering across topics not guaranteed |
| **Topic per service** | Couples topic design to deployment units; refactoring a service splits its topic; violates domain-driven boundaries |

## Consequences

### Positive
- Per-trade ordering within lifecycle/risk/settlement topics — no cross-aggregate coordination needed
- Consumer groups subscribe to exactly the aggregates they care about
- Independent scaling: lifecycle topic can have 12 partitions while EOD has 3
- Schema evolution scoped per aggregate — changing a risk event does not affect lifecycle consumers

### Negative
- Consumers needing cross-aggregate views (e.g., "all events for FX-000042") must join multiple topics
- More topics to monitor operationally (6 vs 1) — mitigated by Grafana topic dashboards
- Partition count tuning per topic adds initial configuration effort

### Mitigations
- `event-sequence-processor` (Kafka Streams) materializes a per-trade view from multiple input topics
- Terraform/Docker Compose templates auto-create topics with correct partition counts
- Grafana dashboard aggregates lag metrics across all `fxops.*` topics
