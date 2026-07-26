# ADR-0005: Event-Driven Architecture with Kafka

## Status
Accepted

## Context
Trade lifecycle processing requires ordered event delivery per trade, decoupled producers/consumers, replay capability, and observability of the event stream for anomaly detection.

## Decision
- Apache Kafka (KRaft mode) as the EVENT_STREAM
- Topic naming: `fxops.{domain}.{entity}[.{qualifier}]`
- Partition key = `tradeId` for trade topics (ordering guarantee per trade)
- Transactional producers for atomic state+event commit
- Dead-letter queues per origin topic with quarantine headers
- Event-sequence-processor (Kafka Streams) detects anomalies without consuming business events
- Schema Registry with BACKWARD compatibility mode
- High-volume Kafka NEVER flows through n8n agents

## Consequences
- Per-trade ordering without global ordering bottleneck
- At-least-once delivery with idempotent consumers (dedup by eventId)
- Replay capability for recovery workflows
- DLQ provides a safety net with triage capability
- Agents receive compact anomaly envelopes, not raw event streams
