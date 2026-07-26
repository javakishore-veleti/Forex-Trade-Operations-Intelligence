# Requirements Document — ElastiCache Redis (Cache on AWS)

> **Technology-agnostic spec.** References `CACHE` Technology Role from
> `01-initial-setup/01-technology-stack`. Resolves via `CloudTargetBinding` → AWS ElastiCache Redis.
> Inherits `architecture-golden-path/01-service-nfrs`.

## Introduction

This spec defines requirements for the managed caching layer backing runtime state, idempotency
keys, and short-lived operational context for `Middleware/` services. It covers cluster topology,
eviction policy, security, connection configuration, and scaling. All example identifiers use
synthetic `FX-` prefixes.

---

## Glossary

- **Cache**: The `CloudTargetBinding` for `CACHE` on AWS → ElastiCache Redis.
- **ClusterMode**: Redis cluster mode distributing keys across shards for scalability.
- **EvictionPolicy**: The rule determining which keys are removed when memory is full.
- **AuthToken**: The token used for Redis AUTH command authentication.
- **ReplicationGroup**: A set of Redis nodes with primary-replica replication.
- **IdempotencyKey**: Short-lived cache entries ensuring exactly-once semantics (GP-Rq-5).

---

## Requirements

### Requirement 1: Cluster Topology and High Availability

**User Story:** As a platform engineer, I want the cache deployed in cluster mode with replicas
across AZs, so that node failure does not lose cached state or cause downtime.

#### Acceptance Criteria

1. THE `Cache` SHALL be deployed in cluster mode with at least 2 shards and 1 replica per shard.
2. REPLICAS SHALL be distributed across different availability zones for HA.
3. AUTOMATIC failover SHALL be enabled — a failed primary promotes a replica within seconds.
4. THE cluster SHALL run the engine version matching the `PinnedVersion` (Redis 7.x).
5. THE cluster SHALL be deployed in data subnets with no public accessibility.

---

### Requirement 2: Eviction Policy

**User Story:** As a service developer, I want a defined eviction policy, so that cache pressure
gracefully removes least-used keys rather than rejecting writes.

#### Acceptance Criteria

1. THE `Cache` SHALL use `allkeys-lru` eviction policy — least-recently-used keys evicted first when memory is full.
2. THE eviction policy SHALL be configurable via parameter group (not hard-coded).
3. IDEMPOTENCY keys (GP-Rq-5) SHALL use TTL-based expiry independent of eviction policy.
4. CACHE misses SHALL NOT cause service failures — services SHALL fall back to authoritative stores.

---

### Requirement 3: Security

**User Story:** As a security engineer, I want all cache traffic encrypted and authenticated,
so that no unauthorized client can read or write cached data.

#### Acceptance Criteria

1. ALL client-to-cache communication SHALL use TLS (in-transit encryption); plaintext SHALL be disabled.
2. AT-REST encryption SHALL be enabled using the AWS-managed or customer-managed key.
3. ACCESS SHALL require an auth token (Redis AUTH) or IAM-based RBAC (Redis 7.x).
4. THE cache SHALL accept connections only from the service namespace security group.
5. THE auth token SHALL be stored in AWS Secrets Manager and rotated on a defined schedule.

---

### Requirement 4: Connection from Services

**User Story:** As a service developer, I want a standard connection pattern, so that all
services connect to the cache consistently with TLS and pooling.

#### Acceptance Criteria

1. SERVICES SHALL connect via the cluster configuration endpoint (not individual node endpoints).
2. THE connection SHALL use TLS with the AWS-provided CA certificate.
3. SPRING BOOT services SHALL use the standard `CACHE` client (Lettuce/Jedis) with connection pooling configured.
4. CONNECTION pool max-active SHALL be bounded (e.g., 16 per pod) to prevent connection exhaustion.
5. CONNECTION timeout SHALL be ≤ 2 seconds; command timeout ≤ 1 second.

---

### Requirement 5: Scaling and Performance

**User Story:** As a capacity planner, I want the cache scaled to the workload with headroom
for burst, so that p99 latency stays sub-millisecond.

#### Acceptance Criteria

1. THE node type SHALL provide sufficient memory for the working set (estimated: 2 GB per shard for trade state, idempotency, and risk context).
2. THE cluster SHALL support online scaling (adding shards) without downtime.
3. CLOUDWATCH metrics (CPU, memory, cache hits/misses, evictions) SHALL be monitored.
4. ALERTS SHALL fire for: eviction rate > 100/min, memory > 80%, replication lag > 1s.

---

### Requirement 6: Cost Considerations

**User Story:** As a FinOps stakeholder, I want cache sized per environment.

#### Acceptance Criteria

1. PRODUCTION SHALL use cluster mode with r7g nodes and Multi-AZ replicas.
2. DEV/TEST SHALL use a single-node or single-shard configuration to minimize cost.
3. RESERVED node pricing SHALL be evaluated for production workloads.
