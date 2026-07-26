# Requirements Document — OpenSearch (Observability Logging on AWS)

> **Technology-agnostic spec.** References `OBSERVABILITY_LOGGING` Technology Role from
> `01-initial-setup/01-technology-stack`. Resolves via `CloudTargetBinding` → AWS OpenSearch.
> Inherits `architecture-golden-path/01-service-nfrs`.

## Introduction

This spec defines requirements for the managed log aggregation and search platform replacing
the local ELK (Elasticsearch + Logstash + Kibana) stack for AWS deployment. It covers the
search domain, ingestion pipeline, dashboards migration, index lifecycle management, and
security. All example identifiers use synthetic `FX-` prefixes.

---

## Glossary

- **ObservabilityLogging**: The `CloudTargetBinding` for `OBSERVABILITY_LOGGING` on AWS → OpenSearch.
- **SearchDomain**: The OpenSearch cluster hosting log indexes.
- **IngestionPipeline**: The path from service structured logs → OpenSearch indexing.
- **DashboardsMigration**: Migrating Kibana saved objects to OpenSearch Dashboards.
- **IndexLifecycle**: The policy governing index rollover, retention, and deletion.
- **SavedQuery**: A pre-defined search query (e.g., trace correlation, error investigation).

---

## Requirements

### Requirement 1: Search Domain Topology

**User Story:** As a platform engineer, I want the logging platform deployed as a managed,
multi-AZ domain, so that log search is always available and performant.

#### Acceptance Criteria

1. THE `ObservabilityLogging` domain SHALL be deployed with at least 2 data nodes across 2 AZs.
2. THE domain SHALL include dedicated master nodes (3, for cluster stability).
3. THE domain SHALL support the OpenSearch version compatible with existing ELK 8.x index schemas.
4. ALL data SHALL be stored on EBS with encryption at rest.
5. THE domain SHALL be deployed in private subnets with no public endpoint.

---

### Requirement 2: Ingestion Pipeline

**User Story:** As an operator, I want structured logs from all services ingested into OpenSearch
via a managed pipeline, so that log correlation works from day one.

#### Acceptance Criteria

1. THE ingestion pipeline SHALL accept structured JSON logs from all `SERVICE_FRAMEWORK` services, sidecars, and the agent platform.
2. THE pipeline SHALL preserve structured fields: `traceId`, `spanId`, `correlationId`, `tradeId`, `service`, `level`, `timestamp`.
3. THE pipeline SHALL support both push-based (Logstash/Fluent Bit) and pull-based (OpenSearch Ingestion) approaches.
4. THE pipeline SHALL NOT lose logs during transient OpenSearch unavailability (buffering/retry).
5. LOG format SHALL match the structured logging format defined in `05-observability/04-otel-log-correlation`.

---

### Requirement 3: Dashboards and Saved Queries Migration

**User Story:** As an operator, I want existing Kibana dashboards and saved queries migrated to
OpenSearch Dashboards, so that operational visibility is maintained.

#### Acceptance Criteria

1. ALL saved queries from `05-observability/04-otel-log-correlation` SHALL be recreated as OpenSearch saved searches.
2. INDEX patterns SHALL be defined for each service log stream (matching the local patterns).
3. DASHBOARD visualizations (if any) SHALL be exported/imported via NDJSON format.
4. THE migration SHALL be scripted and repeatable (not manual point-and-click).

---

### Requirement 4: Index Lifecycle Management

**User Story:** As an operator, I want indexes rotated and expired automatically, so that storage
costs are bounded and search performance is maintained.

#### Acceptance Criteria

1. AN index lifecycle policy SHALL define: hot phase (7 days, full indexing), warm phase (23 days, read-only), delete phase (30 days total retention).
2. INDEXES SHALL roll over when reaching 50 GB or 7 days (whichever first).
3. THE lifecycle policy SHALL apply to all service log indexes.
4. DLQ and audit-trail indexes SHALL have extended retention (90 days) for compliance.

---

### Requirement 5: Security

**User Story:** As a security engineer, I want log access controlled and encrypted.

#### Acceptance Criteria

1. ALL connections to the OpenSearch domain SHALL use HTTPS (TLS in-transit).
2. DATA at rest SHALL be encrypted with KMS (customer-managed key).
3. FINE-GRAINED access control SHALL restrict: service accounts write only their own indexes; operators read all; dashboards access via IAM role.
4. THE domain SHALL have no public endpoint — accessible only via VPC endpoint.
5. AUDIT logging of access to the domain SHALL be enabled.

---

### Requirement 6: Performance and Scaling

**User Story:** As a capacity planner, I want the domain sized for the platform's log volume.

#### Acceptance Criteria

1. THE domain SHALL handle at least 1000 log events/second at sustained ingestion.
2. SEARCH queries across 7-day hot data SHALL return within 5 seconds p95.
3. NODE scaling (adding data nodes, storage) SHALL be documented for burst.
4. ULTRAWARM storage SHALL be evaluated for warm-phase cost reduction.

---

### Requirement 7: Cost Considerations

**User Story:** As a FinOps stakeholder, I want logging costs predictable and sized per environment.

#### Acceptance Criteria

1. PRODUCTION SHALL use r6g data nodes with UltraWarm for warm phase.
2. DEV/TEST SHALL use single-AZ, t3.medium nodes, short retention (7 days).
3. LOG retention reduction is the primary cost lever — dev needs fewer days.
4. RESERVED instance pricing SHALL be evaluated for production nodes.
