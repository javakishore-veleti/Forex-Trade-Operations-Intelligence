# Requirements Document — DocumentDB (Document Store on AWS)

> **Technology-agnostic spec.** References `DOCUMENT_STORE` Technology Role from
> `01-initial-setup/01-technology-stack`. Resolves via `CloudTargetBinding` → AWS DocumentDB.
> Inherits `architecture-golden-path/01-service-nfrs`.

## Introduction

This spec defines requirements for the managed document database backing audit histories,
flexible documents, and denormalized read models in `Middleware/` services. It covers cluster
topology, MongoDB API compatibility, connection string migration, index strategy, security,
and backup. All example identifiers use synthetic `FX-` prefixes.

---

## Glossary

- **DocumentStore**: The `CloudTargetBinding` for `DOCUMENT_STORE` on AWS → Amazon DocumentDB.
- **MongoCompatibility**: The MongoDB wire-protocol version supported by DocumentDB.
- **ConnectionString**: The URI used by services to connect (must migrate from local MongoDB URI format).
- **ClusterEndpoint**: The DNS name for write operations; reader endpoint for reads.
- **IndexStrategy**: The set of database indexes required for query performance.

---

## Requirements

### Requirement 1: Cluster Topology and High Availability

**User Story:** As a platform engineer, I want the document store deployed as a multi-instance
cluster, so that instance failure does not cause data unavailability.

#### Acceptance Criteria

1. THE `DocumentStore` SHALL be deployed as a cluster with at least 1 primary + 1 replica instance.
2. INSTANCES SHALL be distributed across at least 2 availability zones.
3. AUTOMATIC failover SHALL promote a replica to primary within 30 seconds on primary failure.
4. THE cluster SHALL support MongoDB 7.0 wire-protocol compatibility.
5. THE cluster SHALL be deployed in data subnets with no public accessibility.

---

### Requirement 2: Connection String Migration

**User Story:** As a developer, I want the same application code to work against local MongoDB
and AWS DocumentDB, so that no code changes are needed for cloud deployment.

#### Acceptance Criteria

1. SERVICES SHALL connect using a standard MongoDB connection string URI; only the hostname/port and TLS settings change between local and cloud profiles.
2. THE cloud connection string SHALL use the cluster endpoint for writes and reader endpoint for read-preference secondary.
3. TLS SHALL be required for all connections; the AWS `rds-combined-ca-bundle.pem` SHALL be configured as the trusted CA.
4. THE connection string SHALL include `retryWrites=false` (DocumentDB does not support retryable writes).
5. APPLICATION configuration SHALL externalize the connection string per GP-Rq-11 (environment profiles).

---

### Requirement 3: Security

**User Story:** As a security engineer, I want all document-store traffic encrypted and
access restricted to authorized services.

#### Acceptance Criteria

1. ALL client connections SHALL use TLS (in-transit encryption); plaintext connections SHALL be rejected.
2. DATA at rest SHALL be encrypted using a customer-managed KMS key.
3. ACCESS SHALL require username/password authentication; credentials stored in Secrets Manager.
4. THE cluster SHALL accept connections only from the service namespace security group.
5. AUDIT logging SHALL be enabled for DDL/DML operations.

---

### Requirement 4: Index Strategy

**User Story:** As a developer, I want required indexes created at deployment time, so that
queries perform within SLA from day one.

#### Acceptance Criteria

1. ALL indexes required by service queries SHALL be defined in a migration/provisioning script.
2. THE index creation script SHALL be idempotent — re-running SHALL NOT fail on existing indexes.
3. COMPOUND indexes for audit-history queries (e.g., `{tradeId: 1, occurredAt: -1}`) SHALL be defined.
4. TTL indexes SHALL be used for time-limited documents (e.g., operational context expiry).

---

### Requirement 5: Scaling and Performance

**User Story:** As a capacity planner, I want the cluster sized for the audit workload with
read scaling via replicas.

#### Acceptance Criteria

1. READ-heavy workloads SHALL be routed to replica instances via `readPreference=secondaryPreferred`.
2. THE instance class SHALL provide sufficient memory to hold the working set indexes in RAM.
3. CLOUDWATCH metrics (CPU, connections, read/write IOPS, replication lag) SHALL be monitored.
4. AUTOSCALING of replicas SHALL be supported (0–3 additional read replicas for burst).

---

### Requirement 6: Backup and DR

**User Story:** As a compliance officer, I want automated backups with point-in-time recovery.

#### Acceptance Criteria

1. CONTINUOUS backup SHALL be enabled with a retention of at least 7 days.
2. POINT-IN-TIME restore SHALL be available with 5-minute granularity.
3. MANUAL snapshots SHALL be taken before major schema or index changes.
4. CROSS-REGION snapshot copy SHALL be available for DR.

---

### Requirement 7: Cost Considerations

**User Story:** As a FinOps stakeholder, I want costs controlled per environment.

#### Acceptance Criteria

1. PRODUCTION SHALL use db.r6g instances with Multi-AZ replicas.
2. DEV/TEST SHALL use db.t4g.medium single-instance to minimize cost.
3. STORAGE is billed per GB — index optimization reduces storage growth.
