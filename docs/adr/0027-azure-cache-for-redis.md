# ADR-0027: Azure Cache for Redis over Self-Managed Redis on AKS

**Status:** Accepted

**Date:** 2024-02-21

## Context

Redis serves the same roles in Azure as in AWS: idempotency cache for Kafka consumers, agent session memory, HITL approval tokens, and circuit breaker state. The Azure deployment needs a Redis solution with high availability, cluster mode for write scaling, and sub-millisecond latency.

Two options were evaluated:

1. **Azure Cache for Redis** — fully managed Redis with clustering, geo-replication, and automated failover.
2. **Self-managed Redis on AKS** — Redis deployed via Helm/Bitnami chart on Kubernetes.

## Decision

We adopt **Azure Cache for Redis Premium tier with clustering** for Azure deployment.

### Implementation

| Configuration | Value |
|--------------|-------|
| Tier | Premium P1 |
| Clustering | Enabled, 3 shards |
| Node type | 6GB per shard (18GB total) |
| Persistence | AOF (append-only file) for crash recovery |
| Network | Private endpoint in AKS VNet |
| TLS | Required for all connections |
| Auth | Access Keys (rotated via Key Vault) |

### Key Namespaces (Same as AWS)

| Namespace | TTL | Purpose |
|-----------|-----|---------|
| `idem:{serviceId}:{eventId}` | 24 hours | Kafka consumer idempotency |
| `agent:session:{sessionId}` | 4 hours | Agent conversation memory |
| `approval:{tokenId}` | 4 hours | HITL approval tokens |
| `circuit:{serviceName}` | 60 seconds | Circuit breaker state |

### Parity with AWS

Both deployments use the same:
- Key naming conventions and hash-tag routing strategy
- TTL policies per namespace
- Lettuce cluster-mode client configuration in Spring Boot
- Connection pooling parameters

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Self-managed Redis on AKS | HA Redis (Sentinel/Cluster) on Kubernetes is complex; failover less reliable; backup management overhead |
| Azure Cache Basic/Standard tier | No clustering support; single-node throughput ceiling; no persistence |
| Cosmos DB as key-value cache | Higher latency (5-10ms vs sub-ms); overkill for ephemeral cache data |

## Consequences

### Positive
- Managed HA with automatic failover (< 10s failover time)
- Clustering handled by Azure — same shard topology as AWS ElastiCache
- AOF persistence prevents data loss on node restart
- Private endpoint eliminates network exposure
- Same Spring Boot Redis configuration works on both clouds (endpoint swap only)

### Negative
- Premium tier cost is significant for 3-shard cluster (~$500-800/month)
- Azure Cache Redis version may lag behind OSS Redis (typically 1-2 minor versions)
- Limited configuration flexibility compared to self-managed

### Mitigations
- Cost justified by criticality of idempotency cache (duplicate trades are far more expensive)
- Application uses only standard Redis commands — no dependency on bleeding-edge features
- Azure Cache supports Redis 7.x — sufficient for all platform requirements
