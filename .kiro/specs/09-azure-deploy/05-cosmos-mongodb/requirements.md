# Requirements Document — Cosmos DB MongoDB API (Document Store on Azure)

> **Technology-agnostic spec.** References `DOCUMENT_STORE` Technology Role from
> `01-initial-setup/01-technology-stack`. Resolves via `CloudTargetBinding` → Azure Cosmos DB for MongoDB.
> Inherits `architecture-golden-path/01-service-nfrs`.

## Introduction

This spec defines requirements for the managed document store backing audit histories and flexible
documents for `Middleware/` services on Azure. Cosmos DB with MongoDB API provides wire-protocol
compatibility with local MongoDB, requiring minimal application changes. It covers partition key
strategy, RU provisioning, consistency levels, connection string migration, indexing, and security.
All example identifiers use synthetic `FX-` prefixes.

---

## Glossary

- **DocumentStore**: The `CloudTargetBinding` for `DOCUMENT_STORE` on Azure → Cosmos DB for MongoDB (vCore or RU-based).
- **PartitionKey**: The field used to distribute documents across physical partitions for horizontal scale.
- **RequestUnit (RU)**: Cosmos DB's throughput currency — a normalized measure of read/write cost.
- **ConsistencyLevel**: The tunable consistency guarantee (Strong, Bounded Staleness, Session, Eventual).
- **ConnectionStringMigration**: Changing the MongoDB connection string from local to Cosmos DB endpoint without code changes.
- **AutoscaleThroughput**: Cosmos DB's auto-scale mode where RU/s scale between min and max based on demand.

---

## Requirements

### Requirement 1: Account Topology and Availability

**User Story:** As a platform engineer, I want the document store deployed with multi-zone
redundancy, so that audit data is resilient to single-zone failure.

#### Acceptance Criteria

1. THE `DocumentStore` SHALL be deployed as a Cosmos DB account with MongoDB API (version 7.0 compatible).
2. THE account SHALL enable availability zones for zone-redundant replication within the primary region.
3. GEO-REDUNDANCY SHALL be configurable (enabled for prod, disabled for dev) for cross-region DR.
4. THE account SHALL be deployed with VNet integration (Private Endpoint) — no public access.
5. THE account SHALL support the MongoDB wire protocol version used by the Spring Data MongoDB driver.

---

### Requirement 2: Partition Key Strategy

**User Story:** As a data architect, I want a partition key strategy that distributes load evenly
and supports the platform's query patterns, so that no hot partition degrades performance.

#### Acceptance Criteria

1. TRADE audit history collections SHALL use `tradeId` as the partition key (queries are always trade-scoped).
2. LIFECYCLE event collections SHALL use `tradeId` as the partition key for co-location with audit data.
3. REFERENCE/LOOKUP collections (e.g., counterparty, trading book) SHALL use the document's natural key as partition key.
4. NO collection SHALL use a partition key that creates unbounded growth in a single partition (≤ 20 GB per logical partition).
5. CROSS-PARTITION queries SHALL be minimized by design; collection schema SHALL support single-partition reads for primary access patterns.

---

### Requirement 3: Throughput Provisioning (RU/s)

**User Story:** As a capacity planner, I want throughput provisioned to handle trade-volume
peaks without throttling, with auto-scale to avoid over-provisioning.

#### Acceptance Criteria

1. PRODUCTION collections SHALL use autoscale throughput (min 400 RU/s, max configurable per collection based on load testing).
2. THE platform SHALL provision sufficient RU/s for the expected write rate (trade audit ≥ 500 writes/sec peak).
3. DEV/TEST collections SHALL use manual throughput at minimum RU/s (400) to minimize cost.
4. SHARED database-level throughput SHALL be used where multiple low-traffic collections share capacity.
5. THE platform SHALL monitor and alert on 429 (throttling) responses; sustained throttling triggers scale-up.

---

### Requirement 4: Connection String Migration

**User Story:** As a service developer, I want to switch from local MongoDB to Cosmos DB by
changing only the connection string, so that no application code changes are required.

#### Acceptance Criteria

1. SERVICES SHALL connect using the standard MongoDB connection string format (`mongodb+srv://` or `mongodb://`).
2. THE Cosmos DB connection string SHALL be stored in Azure Key Vault and injected as an environment variable.
3. THE Spring Data MongoDB driver configuration (`spring.data.mongodb.uri`) SHALL be the only property that differs between local and Azure environments.
4. WRITE concern and read preference settings SHALL be validated for Cosmos DB compatibility.
5. FEATURES unsupported by Cosmos DB MongoDB API (e.g., `$graphLookup`, multi-document ACID transactions on vCore) SHALL be documented and mitigated.

---

### Requirement 5: Indexing Strategy

**User Story:** As a developer, I want efficient indexes supporting the platform's query patterns,
so that reads are fast and RU consumption is minimized.

#### Acceptance Criteria

1. EACH collection SHALL define explicit indexes for its primary query patterns (Cosmos DB does not auto-index all fields by default in MongoDB API).
2. COMPOUND indexes SHALL be created for queries that filter on multiple fields.
3. THE platform SHALL use the `_id` field as the default unique index per document.
4. WILDCARD indexes SHALL be avoided to control RU consumption on writes.
5. INDEX creation SHALL be automated via application startup or migration scripts.

---

### Requirement 6: Security

**User Story:** As a security engineer, I want the document store encrypted and accessible only
from authorized services.

#### Acceptance Criteria

1. ALL connections SHALL use TLS (minimum TLS 1.2); plaintext connections SHALL be rejected.
2. AUTHENTICATION SHALL use Cosmos DB resource tokens or Azure AD (Entra ID) RBAC where supported.
3. DATA at rest SHALL be encrypted (Azure-managed or customer-managed key via Key Vault).
4. THE account SHALL be accessible only via Private Endpoint — no public network access.
5. DIAGNOSTIC logs (data plane and control plane) SHALL be enabled and sent to Azure Monitor.

---

### Requirement 7: Cost Considerations

**User Story:** As a FinOps stakeholder, I want document store costs predictable and controlled.

#### Acceptance Criteria

1. PRODUCTION SHALL use autoscale throughput with defined max RU/s cap per collection.
2. DEV/TEST SHALL use serverless or minimum manual throughput.
3. RESERVED capacity (1-year) SHALL be evaluated for production workloads.
4. TTL (time-to-live) SHALL be configured on transient collections to auto-expire old documents and reduce storage costs.
5. ALL Cosmos DB resources SHALL be tagged with `project:fxops`, `environment:<env>`.
