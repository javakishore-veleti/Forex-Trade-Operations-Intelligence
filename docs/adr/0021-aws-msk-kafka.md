# ADR-0021: AWS MSK (Managed Streaming for Kafka) over Self-Managed

**Status:** Accepted

**Date:** 2024-02-18

## Context

Kafka is the backbone of the event-driven architecture — 25+ domain event types flow through it, powering service decoupling, the event-sequence-processor (Kafka Streams), and DLQ management. The operational choice between managed (MSK) and self-managed Kafka on EKS affects reliability, cost, and team bandwidth.

Two options were evaluated:

1. **Amazon MSK** — fully managed Kafka with automated broker patching, storage scaling, and monitoring.
2. **Self-managed Kafka on EKS** — Strimzi operator deploying Kafka brokers as StatefulSets.

## Decision

We adopt **Amazon MSK (provisioned)** for production Kafka workloads.

### Implementation

| Configuration | Value |
|--------------|-------|
| Kafka version | 3.6.x |
| Broker type | kafka.m5.large (3 brokers across 3 AZs) |
| Storage | 500 GB per broker, auto-expanding |
| Authentication | IAM + SASL/SCRAM for service accounts |
| Encryption | TLS in-transit, KMS at-rest |
| Topic creation | Terraform-managed; no auto-create |

### Topic Layout

- `fx.trade.events` — 12 partitions (keyed by trade ID)
- `fx.risk.events` — 6 partitions
- `fx.agent.approval-requests` — 3 partitions
- `fx.sidecar.detections` — 3 partitions
- `fx.dlq.*` — per-service DLQ topics (3 partitions each)

### Kafka Streams

The `event-sequence-processor` runs on EKS and consumes from MSK. It uses IAM authentication and connects via the private endpoint (no internet exposure).

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Self-managed (Strimzi on EKS) | Operational burden: broker upgrades, rebalancing, storage management, ZooKeeper/KRaft migration — team prefers managed |
| MSK Serverless | Partition limit (200) too low for future growth; no Kafka Streams state store support at launch |
| Amazon Kinesis | Not Kafka-compatible; would require rewriting all producers/consumers; no Kafka Streams |

## Consequences

### Positive
- Zero broker management: patching, storage expansion, and rack-awareness handled by AWS
- Multi-AZ by default — 3 brokers across 3 AZs with managed replication
- IAM-based auth integrates with existing AWS access patterns
- CloudWatch metrics + Open Monitoring (Prometheus) for comprehensive observability
- Tiered storage (planned) will reduce long-retention costs

### Negative
- Higher cost than self-managed for equivalent compute (~30-40% premium)
- Less flexibility in Kafka configuration (some broker configs not modifiable)
- MSK version updates lag community Kafka by 1-2 months
- No custom plugins (cannot deploy custom interceptors on brokers)

### Mitigations
- Cost premium offset by eliminated ops engineering hours (estimated 0.5 FTE saved)
- Application logic (Streams, consumers) runs on EKS — full flexibility where it matters
- Version lag acceptable for stability; no dependency on bleeding-edge features
