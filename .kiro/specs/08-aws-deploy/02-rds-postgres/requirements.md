# Requirements Document — RDS PostgreSQL (Relational Store on AWS)

> **Technology-agnostic spec.** References `RELATIONAL_STORE` Technology Role from
> `01-initial-setup/01-technology-stack`. Resolves via `CloudTargetBinding` → AWS RDS PostgreSQL.
> Inherits `architecture-golden-path/01-service-nfrs`.

## Introduction

This spec defines requirements for the managed relational database backing all `Middleware/`
services that persist transactional trade state. It covers instance topology, connection
management, schema migration strategy, backup/recovery, security, and performance tuning.
All example identifiers use synthetic `FX-` prefixes.

---

## Glossary

- **RelationalStore**: The `CloudTargetBinding` for `RELATIONAL_STORE` on AWS → RDS PostgreSQL.
- **ConnectionPool**: A pool proxy between services and the database reducing connection overhead.
- **SchemaMigration**: The versioned, repeatable process of evolving database schema (Flyway).
- **MultiAZ**: Active-standby replication across availability zones for HA failover.
- **ParameterGroup**: The set of database engine tuning parameters applied to the instance.
- **BackupWindow**: The daily automated snapshot window.

---

## Requirements

### Requirement 1: Instance Topology and High Availability

**User Story:** As a platform engineer, I want the relational store deployed in a Multi-AZ
configuration, so that a single-AZ failure does not cause data loss or prolonged downtime.

#### Acceptance Criteria

1. THE `RelationalStore` SHALL be deployed as a Multi-AZ instance with synchronous replication to a standby in a different AZ.
2. THE instance SHALL run the engine version matching the `PinnedVersion` in the technology-stack registry (PostgreSQL 16.x).
3. AUTOMATIC failover SHALL complete within 60 seconds and require no application code changes.
4. THE instance SHALL be deployed in the data subnets (private, no public accessibility).
5. THE instance SHALL support at least 200 concurrent connections from all services combined.

---

### Requirement 2: Connection Pooling

**User Story:** As a service developer, I want a connection pool between services and the database,
so that connection exhaustion under load is prevented and connection setup latency is minimized.

#### Acceptance Criteria

1. A `ConnectionPool` proxy SHALL sit between `SERVICE_FRAMEWORK` pods and the `RelationalStore`.
2. THE pool SHALL support connection multiplexing (many clients sharing fewer DB connections).
3. THE pool SHALL enforce a max-connections-per-service limit to prevent a single service from exhausting the pool.
4. THE pool endpoint SHALL be the only connection path from services — direct database connections from pods SHALL be blocked by security groups.
5. THE pool SHALL support TLS passthrough to the database.

---

### Requirement 3: Security

**User Story:** As a security engineer, I want the database accessible only from authorized
services within the VPC, encrypted at rest and in transit.

#### Acceptance Criteria

1. THE `RelationalStore` SHALL accept connections only from the service namespace security group — no public endpoint.
2. ALL connections SHALL use TLS (minimum TLS 1.2); plaintext connections SHALL be rejected.
3. DATA at rest SHALL be encrypted using a customer-managed encryption key.
4. DATABASE credentials SHALL be stored in a secrets manager and rotated automatically (≤ 90 days).
5. IAM database authentication SHALL be supported for service identity-based access.

---

### Requirement 4: Schema Migration Strategy

**User Story:** As a developer, I want database schema changes applied via versioned, repeatable
migrations, so that every environment is provably at the same schema version.

#### Acceptance Criteria

1. SCHEMA migrations SHALL use a versioned migration tool (per `SERVICE_BUILD_TOOL` ecosystem) applied at deployment time, before application startup.
2. MIGRATIONS SHALL be forward-only; rollback of destructive changes requires a new forward migration.
3. EACH Middleware service that owns schema tables SHALL maintain its own migration directory (per-service schema isolation within the same database or separate databases).
4. MIGRATION execution SHALL be idempotent — re-running a completed migration SHALL have no effect.
5. THE migration tool SHALL fail the deployment if a migration cannot be applied cleanly.

---

### Requirement 5: Performance and Parameter Tuning

**User Story:** As a DBA, I want database parameters tuned for the OLTP workload profile of
trade operations, so that write-heavy operations (trade capture, state transitions) perform within SLA.

#### Acceptance Criteria

1. A custom `ParameterGroup` SHALL be defined (not the default) with tuning for: `shared_buffers`, `work_mem`, `max_connections`, `effective_cache_size`, `checkpoint_completion_target`.
2. THE `RelationalStore` SHALL use provisioned IOPS storage (not burstable gp2) for predictable latency.
3. SLOW query logging SHALL be enabled (threshold ≤ 500ms) with logs shipped to the observability stack.
4. CONNECTION timeout SHALL be configured at 5 seconds; statement timeout at 30 seconds.

---

### Requirement 6: Backup, Retention, and DR

**User Story:** As a compliance officer, I want automated backups with defined retention and
point-in-time recovery, so that data loss is bounded and recovery is testable.

#### Acceptance Criteria

1. AUTOMATED daily snapshots SHALL be retained for at least 30 days.
2. POINT-IN-TIME recovery (PITR) SHALL be available with a 5-minute recovery point objective (RPO).
3. CROSS-REGION snapshot copy SHALL be enabled for DR readiness.
4. THE platform SHALL document and periodically test a restore-from-backup runbook.
5. MANUAL snapshots SHALL be taken before and after major schema migrations.

---

### Requirement 7: Cost Considerations

**User Story:** As a FinOps stakeholder, I want the database sized appropriately per environment,
so that dev/test costs are minimized without compromising production resilience.

#### Acceptance Criteria

1. PRODUCTION SHALL use Multi-AZ with provisioned IOPS and the recommended instance class.
2. DEV/TEST environments SHALL use single-AZ, smaller instance classes, and gp3 storage.
3. RESERVED instance pricing or Savings Plans SHALL be evaluated for production workloads.
4. STORAGE auto-scaling SHALL be enabled with a defined maximum to prevent unbounded cost.
