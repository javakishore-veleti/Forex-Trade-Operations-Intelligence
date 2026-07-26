# Requirements Document — Azure Database for PostgreSQL (Relational Store on Azure)

> **Technology-agnostic spec.** References `RELATIONAL_STORE` Technology Role from
> `01-initial-setup/01-technology-stack`. Resolves via `CloudTargetBinding` → Azure Database for PostgreSQL.
> Inherits `architecture-golden-path/01-service-nfrs`.

## Introduction

This spec defines requirements for the managed relational database backing all `Middleware/`
services that persist transactional trade state on Azure. It covers instance topology, connection
pooling (PgBouncer built-in), schema migration strategy, backup/recovery, security, firewall,
and performance tuning. All example identifiers use synthetic `FX-` prefixes.

---

## Glossary

- **RelationalStore**: The `CloudTargetBinding` for `RELATIONAL_STORE` on Azure → Azure Database for PostgreSQL Flexible Server.
- **PgBouncer**: Built-in connection pooler in Flexible Server for transaction-mode pooling.
- **SchemaMigration**: Versioned, repeatable schema evolution (Flyway).
- **ZoneRedundantHA**: Synchronous replication to standby in a different availability zone.
- **FirewallRule**: Network-level ACL restricting which VNet subnets may connect to the database.
- **FlexibleServer**: Azure's current-generation managed PostgreSQL offering with fine-grained control.

---

## Requirements

### Requirement 1: Instance Topology and High Availability

**User Story:** As a platform engineer, I want the relational store deployed with zone-redundant
HA, so that a single-zone failure does not cause data loss or prolonged downtime.

#### Acceptance Criteria

1. THE `RelationalStore` SHALL be deployed as a Flexible Server with zone-redundant HA (synchronous commit to standby in a different AZ).
2. THE instance SHALL run the engine version matching the `PinnedVersion` (PostgreSQL 16.x).
3. AUTOMATIC failover SHALL complete within 60 seconds and require no application code changes.
4. THE instance SHALL be deployed with VNet integration (delegated subnet, no public endpoint).
5. THE instance SHALL support at least 200 concurrent connections from all services combined.

---

### Requirement 2: Connection Pooling (Built-in PgBouncer)

**User Story:** As a service developer, I want built-in connection pooling between services and the
database, so that connection exhaustion is prevented and setup latency is minimized.

#### Acceptance Criteria

1. THE Flexible Server SHALL have built-in PgBouncer enabled in transaction mode.
2. THE pool SHALL support connection multiplexing (many application connections sharing fewer DB connections).
3. THE PgBouncer `default_pool_size` SHALL be configured to prevent a single service from exhausting the pool.
4. THE PgBouncer endpoint (port 6432) SHALL be the primary connection path from services.
5. THE pool SHALL support TLS passthrough to the database engine.

---

### Requirement 3: Security and Firewall

**User Story:** As a security engineer, I want the database accessible only from authorized
services within the VNet, encrypted at rest and in transit.

#### Acceptance Criteria

1. THE `RelationalStore` SHALL accept connections only from the delegated AKS subnet — no public endpoint.
2. ALL connections SHALL use TLS (minimum TLS 1.2); plaintext connections SHALL be rejected via `require_secure_transport = ON`.
3. DATA at rest SHALL be encrypted using a customer-managed key (Azure Key Vault CMK).
4. DATABASE credentials SHALL be stored in Azure Key Vault and rotated automatically (≤ 90 days).
5. AZURE AD (Entra ID) authentication SHALL be supported for managed-identity-based access from pods.
6. FIREWALL rules SHALL explicitly allow only the AKS VNet subnet CIDR; deny all other sources.

---

### Requirement 4: Schema Migration Strategy (Flyway)

**User Story:** As a developer, I want schema changes applied via versioned migrations at
deployment time, so that every environment is provably at the same schema version.

#### Acceptance Criteria

1. SCHEMA migrations SHALL use Flyway applied at deployment time (init container or startup hook), before application serves traffic.
2. MIGRATIONS SHALL be forward-only; rollback requires a new forward migration.
3. EACH Middleware service that owns schema tables SHALL maintain its own migration directory.
4. MIGRATION execution SHALL be idempotent — re-running a completed migration SHALL have no effect.
5. THE migration tool SHALL fail the deployment if a migration cannot be applied cleanly.

---

### Requirement 5: Performance and Parameter Tuning

**User Story:** As a DBA, I want database parameters tuned for the OLTP workload profile of
trade operations, so that write-heavy operations perform within SLA.

#### Acceptance Criteria

1. CUSTOM server parameters SHALL be configured: `shared_buffers`, `work_mem`, `max_connections`, `effective_cache_size`, `checkpoint_completion_target`.
2. THE `RelationalStore` SHALL use Premium SSD v2 or Premium SSD storage with provisioned IOPS for predictable latency.
3. SLOW query logging SHALL be enabled (threshold ≤ 500ms) with logs shipped to Azure Monitor.
4. CONNECTION timeout SHALL be configured at 5 seconds; statement timeout at 30 seconds.

---

### Requirement 6: Backup, Retention, and DR

**User Story:** As a compliance officer, I want automated backups with defined retention and
point-in-time recovery, so that data loss is bounded and recovery is testable.

#### Acceptance Criteria

1. AUTOMATED backups SHALL be retained for at least 30 days.
2. POINT-IN-TIME recovery (PITR) SHALL be available with a 5-minute RPO.
3. GEO-REDUNDANT backup SHALL be enabled for DR readiness (paired region).
4. THE platform SHALL document and periodically test a restore-from-backup runbook.
5. MANUAL snapshots SHALL be taken before and after major schema migrations.

---

### Requirement 7: Cost Considerations

**User Story:** As a FinOps stakeholder, I want the database sized appropriately per environment,
so that dev/test costs are minimized without compromising production resilience.

#### Acceptance Criteria

1. PRODUCTION SHALL use zone-redundant HA with Premium SSD and the recommended compute tier (General Purpose or Memory Optimized).
2. DEV/TEST environments SHALL use Burstable tier, single-zone, smaller compute SKU.
3. RESERVED capacity SHALL be evaluated for production workloads (1-year or 3-year).
4. STORAGE auto-grow SHALL be enabled with a defined maximum to prevent unbounded cost.
