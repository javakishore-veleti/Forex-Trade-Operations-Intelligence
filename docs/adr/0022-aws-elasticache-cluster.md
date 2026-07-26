# ADR-0022: AWS ElastiCache Cluster Mode for Idempotency Cache

**Status:** Accepted

**Date:** 2024-02-19

## Context

Redis serves two roles in the platform: (1) idempotency cache for at-least-once Kafka consumers (preventing duplicate trade processing), and (2) session memory for agent conversations. The idempotency cache must be highly available — if it fails, duplicate trades could be processed. The cache must handle high write throughput during market hours.

Two ElastiCache deployment modes were evaluated:

1. **Single-node (or non-clustered replication group)** — one primary + read replicas, single shard.
2. **Cluster mode** — data sharded across multiple nodes with automatic partitioning.

## Decision

We adopt **ElastiCache cluster mode enabled** with 3 shards for the idempotency cache workload.

### Implementation

| Configuration | Value |
|--------------|-------|
| Engine | Redis 7.x (OSS compatible) |
| Mode | Cluster mode enabled |
| Shards | 3 (each: 1 primary + 1 replica = 6 nodes total) |
| Node type | cache.r6g.large |
| Encryption | In-transit TLS + at-rest KMS |
| Auth | Redis AUTH token (rotated via Secrets Manager) |

### Key Namespaces

| Namespace | TTL | Purpose |
|-----------|-----|---------|
| `idem:{serviceId}:{eventId}` | 24 hours | Kafka consumer idempotency keys |
| `agent:session:{sessionId}` | 4 hours | Agent conversation memory |
| `approval:{tokenId}` | 4 hours | HITL approval tokens (ADR-0014) |
| `circuit:{serviceName}` | 60 seconds | Circuit breaker state (ADR-0015) |

### Why Cluster Mode

- Idempotency key volume: ~500K keys during peak hour (all active trades × event types)
- Single shard memory limit would require oversized nodes; sharding distributes evenly
- Hash-tag routing: `{serviceId}` ensures all keys for one service hit the same shard (atomic operations)

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Single-node (non-clustered) | Memory ceiling on single node (~100GB max); no write scaling; failover promotes replica (slower) |
| Self-managed Redis on EKS | Operational burden for HA Redis; persistence/backup management; no automatic failover |
| DynamoDB for idempotency | Higher latency (single-digit ms vs sub-ms); more expensive for high-throughput key-value writes |

## Consequences

### Positive
- Write throughput scales linearly with shard count
- Sub-millisecond latency for idempotency checks (critical path for trade processing)
- Automatic failover per shard — partial failure doesn't affect all services
- Memory scales horizontally — no single-node ceiling

### Negative
- Cluster mode requires hash-tag aware key design (cross-shard operations not atomic)
- Higher cost than single-node (6 nodes vs 2)
- Client must use cluster-aware Redis driver (Lettuce in Spring Boot supports this natively)

### Mitigations
- Key design uses `{serviceId}` hash tags — all related keys route to same shard
- Cost justified by avoiding duplicate trade processing (single duplicate could cost more than annual cache cost)
- Spring Boot's Lettuce driver handles cluster topology transparently
