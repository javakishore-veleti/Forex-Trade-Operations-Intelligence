# Requirements Document — Azure Cache for Redis (Cache on Azure)

> **Technology-agnostic spec.** References `CACHE` Technology Role from
> `01-initial-setup/01-technology-stack`. Resolves via `CloudTargetBinding` → Azure Cache for Redis.
> Inherits `architecture-golden-path/01-service-nfrs`.

## Introduction

This spec defines requirements for the managed cache layer backing runtime state, idempotency
keys, and short-lived context for `Middleware/` services on Azure. It covers instance topology,
clustering, TLS connectivity, Spring Boot Lettuce client configuration, eviction policy, and
security. All example identifiers use synthetic `FX-` prefixes.

---

## Glossary

- **CacheStore**: The `CloudTargetBinding` for `CACHE` on Azure → Azure Cache for Redis.
- **ClusterMode**: Redis Cluster topology distributing data across multiple shards for horizontal scale.
- **TLSPort**: The encrypted connection port (6380) used by Azure Cache for Redis.
- **LettuceClient**: The reactive Redis client used by `SERVICE_FRAMEWORK` (Spring Boot default).
- **EvictionPolicy**: The strategy for removing keys when memory is exhausted.
- **PrivateEndpoint**: Azure Private Link endpoint restricting cache access to the VNet.

---

## Requirements

### Requirement 1: Instance Topology and High Availability

**User Story:** As a platform engineer, I want the cache deployed with zone redundancy and
clustering, so that cache availability matches the SLA of the services depending on it.

#### Acceptance Criteria

1. THE `CacheStore` SHALL be deployed as Azure Cache for Redis at Premium or Enterprise tier with zone redundancy.
2. THE instance SHALL run Redis engine version 7.x matching the `PinnedVersion`.
3. CLUSTER mode SHALL be enabled with at least 2 shards for production (horizontal data distribution).
4. EACH shard SHALL have at least 1 replica for HA failover.
5. THE instance SHALL be deployed with VNet integration (Private Endpoint) — no public access.

---

### Requirement 2: Connection from Spring Boot (Lettuce)

**User Story:** As a service developer, I want to connect to Azure Cache for Redis using the
standard Spring Boot Lettuce client, so that no application code changes are required vs. local Redis.

#### Acceptance Criteria

1. SERVICES SHALL connect using `spring.data.redis.host` and `spring.data.redis.port` (6380 TLS) with only endpoint and credentials overridden per environment.
2. THE Lettuce client SHALL be configured for cluster-aware mode when cluster mode is enabled.
3. TLS SHALL be enabled on the Lettuce connection (`spring.data.redis.ssl.enabled=true`).
4. CONNECTION pooling (Lettuce connection pool via Commons Pool 2) SHALL be configured with bounded min/max connections.
5. READ replicas SHALL be used for read operations where supported to distribute load.

---

### Requirement 3: Security and Encryption

**User Story:** As a security engineer, I want the cache encrypted in transit and accessible
only from authorized services.

#### Acceptance Criteria

1. ALL connections SHALL use TLS (port 6380); non-TLS port (6379) SHALL be disabled.
2. AUTHENTICATION SHALL use Azure AD (Entra ID) access tokens via Managed Identity (preferred) or access keys stored in Key Vault.
3. ACCESS keys SHALL be rotated on a defined schedule (≤ 90 days) if used as fallback.
4. THE cache SHALL be accessible only via Private Endpoint — no public network access.
5. DATA at rest SHALL be encrypted (Azure-managed or customer-managed key).

---

### Requirement 4: Eviction and Memory Policy

**User Story:** As a service developer, I want a defined eviction policy, so that cache behavior
under memory pressure is predictable and does not cause data corruption.

#### Acceptance Criteria

1. THE eviction policy SHALL be `allkeys-lru` (evict least-recently-used keys across all keyspaces).
2. MAXIMUM memory SHALL be sized to hold runtime state for all active trades + idempotency keys + short-lived context without routine eviction.
3. KEY TTL SHALL be enforced for all cached values — no indefinite keys except configuration lookups.
4. SERVICES SHALL treat cache as ephemeral — all data recoverable from `RELATIONAL_STORE` or `EVENT_STREAM`.

---

### Requirement 5: Monitoring and Diagnostics

**User Story:** As an operator, I want cache metrics and alerts, so that performance degradation
is detected before it impacts services.

#### Acceptance Criteria

1. AZURE Monitor metrics (cache hits, misses, connected clients, memory usage, server load) SHALL be collected.
2. ALERTS SHALL fire when memory usage exceeds 80% or cache hit ratio drops below 90%.
3. SLOW-LOG SHALL be enabled and queryable for debugging latency issues.
4. DIAGNOSTIC logs SHALL be sent to the observability pipeline (Azure Monitor / Log Analytics).

---

### Requirement 6: Cost Considerations

**User Story:** As a FinOps stakeholder, I want cache costs controlled per environment.

#### Acceptance Criteria

1. PRODUCTION SHALL use Premium tier with clustering and zone redundancy.
2. DEV/TEST environments SHALL use Basic or Standard tier (single shard, no clustering).
3. RESERVED capacity SHALL be evaluated for production (1-year or 3-year commitment).
4. ALL cache resources SHALL be tagged with `project:fxops`, `environment:<env>`.
