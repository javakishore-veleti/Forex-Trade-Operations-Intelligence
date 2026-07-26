# ADR-0016: Kafka Streams for Event Sequence Processor

## Status
Accepted

## Context
The `event-sequence-processor` observes all domain events for a trade and detects sequence anomalies:
- Out-of-order transitions (e.g., `SETTLED` before `MATCHED`)
- Missing events (gap in sequence numbers)
- Duplicate events (same `eventId` seen twice)
- Timing violations (enrichment took > 30 seconds)

This processor must:
- Consume from multiple input topics (`fxops.trade.lifecycle`, `fxops.trade.risk`, etc.)
- Maintain per-trade state (last seen sequence number, expected next event)
- Emit anomaly events to `fxops.system.anomalies` without disrupting source topic consumers
- Scale horizontally as trade volume grows

## Decision
Implement the event-sequence-processor as a **Kafka Streams application** using the Streams DSL.

```java
StreamsBuilder builder = new StreamsBuilder();
KStream<String, DomainEvent> lifecycle = builder.stream("fxops.trade.lifecycle");
KStream<String, DomainEvent> risk = builder.stream("fxops.trade.risk");

lifecycle.merge(risk)
    .groupByKey()
    .aggregate(TradeEventState::empty, (tradeId, event, state) -> state.accept(event),
        Materialized.as("trade-event-state-store"))
    .toStream()
    .filter((tradeId, state) -> state.hasAnomaly())
    .mapValues(TradeEventState::toAnomalyEnvelope)
    .to("fxops.system.anomalies");
```

Key properties:
- State store (`RocksDB`) maintains per-trade sequence tracking without external DB
- Kafka Streams handles rebalancing, fault tolerance, and exactly-once semantics
- Co-partitioning by `tradeId` ensures all events for a trade hit the same processor instance

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| **Spring Kafka `@KafkaListener` consumer** | No built-in state store; would need external Redis/DB for per-trade state; manual partition assignment for stateful processing |
| **Apache Flink** | Far more powerful but massive operational overhead for a single stream job; requires separate Flink cluster; justified only at 100K+ events/sec |
| **Custom poller with in-memory state** | Loses state on restart; no automatic rebalancing; no exactly-once guarantees; re-invents what Kafka Streams provides |
| **ksqlDB** | Limited expressiveness for complex anomaly detection logic; harder to unit-test; SQL not ideal for stateful sequence validation |

## Consequences

### Positive
- Built-in state management (RocksDB) with changelog topic for fault tolerance
- Horizontal scaling by adding instances — Kafka handles partition reassignment
- Exactly-once semantics prevent duplicate anomaly emissions
- Unit-testable with `TopologyTestDriver` (no running Kafka needed for tests)
- Lightweight — runs as a regular Spring Boot app, no separate cluster

### Negative
- RocksDB state stores consume local disk; large state requires adequate storage
- Kafka Streams threading model requires understanding for tuning (`num.stream.threads`)
- Rebalancing during scale-out causes brief processing pauses
- Debugging stateful stream topology is harder than simple consumers

### Mitigations
- State store size bounded by trade TTL (trades older than 30 days are tombstoned)
- Interactive queries expose state store contents for debugging (`/api/state/{tradeId}`)
- Comprehensive topology tests cover all anomaly patterns (35+ test cases)
