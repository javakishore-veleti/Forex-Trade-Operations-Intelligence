# Requirements Document — Neptune / Neo4j (Graph Store on AWS)

> **Technology-agnostic spec.** References `GRAPH_STORE` Technology Role from
> `01-initial-setup/01-technology-stack`. Resolves via `CloudTargetBinding` → AWS Neptune
> (or self-managed Neo4j on EKS). Inherits `architecture-golden-path/01-service-nfrs`.

## Introduction

This spec defines requirements for the graph database backing dependency traversal, contagion
analysis, and relationship graphs in the platform. It covers deployment strategy (managed vs
self-managed), query language support, data migration from local Neo4j, security, and
performance. All example identifiers use synthetic `FX-` prefixes.

---

## Glossary

- **GraphStore**: The `CloudTargetBinding` for `GRAPH_STORE` on AWS → Neptune or Neo4j on EKS.
- **QueryLanguage**: The graph query language used (openCypher preferred for Neo4j compatibility).
- **DataMigration**: The process of transferring graph data from local Neo4j to the cloud target.
- **NeptuneEndpoint**: The cluster endpoint for graph read/write operations.
- **TraversalQuery**: A multi-hop graph query (e.g., blast radius, contagion path).

---

## Requirements

### Requirement 1: Deployment Strategy

**User Story:** As a platform engineer, I want a graph store on AWS that supports openCypher
queries, so that existing local Neo4j Cypher queries work with minimal changes.

#### Acceptance Criteria

1. THE `GraphStore` deployment SHALL support openCypher query language for compatibility with local Neo4j queries.
2. THE deployment SHALL choose between: (a) AWS Neptune with openCypher support, or (b) self-managed Neo4j on EKS if Neptune compatibility gaps block critical queries.
3. THE chosen option SHALL be documented in an ADR with trade-off analysis (managed vs self-managed, cost, ops burden).
4. THE deployment SHALL provide HA with at least 1 read replica in a different AZ.
5. THE cluster SHALL be deployed in data subnets with no public accessibility.

---

### Requirement 2: Query Language and Compatibility

**User Story:** As a developer, I want openCypher queries to work against the cloud graph store,
so that contagion-analysis and relationship agents function correctly.

#### Acceptance Criteria

1. THE graph store SHALL support openCypher for read and write queries.
2. WHERE Neptune is chosen, ANY unsupported Cypher features (e.g., APOC procedures) SHALL be identified and alternative implementations documented.
3. THE platform SHALL provide a compatibility layer or query abstraction if direct 1:1 query translation is not possible.
4. WRITE operations (node/edge creation) SHALL support transactions for atomicity.

---

### Requirement 3: Data Migration

**User Story:** As a deployer, I want a repeatable migration of graph data from local Neo4j
to the cloud target, so that cloud deploys start with the correct relationship model.

#### Acceptance Criteria

1. A migration script SHALL export local Neo4j nodes and relationships to a portable format (CSV or JSON).
2. THE import process SHALL load the exported data into the cloud graph store idempotently.
3. THE migration SHALL preserve all node labels, relationship types, and properties.
4. THE migration SHALL be tested with synthetic `FX-` prefixed data (no real data).
5. SCHEMA constraints (uniqueness, existence) SHALL be recreated in the cloud target.

---

### Requirement 4: Security

**User Story:** As a security engineer, I want graph database access restricted and encrypted.

#### Acceptance Criteria

1. ALL connections SHALL use TLS (in-transit encryption).
2. DATA at rest SHALL be encrypted using a KMS key.
3. ACCESS SHALL require IAM-based authentication (Neptune) or username/password (Neo4j on EKS) — credentials in Secrets Manager.
4. THE graph store SHALL accept connections only from the service namespace security group.
5. AUDIT logging SHALL be enabled for query operations.

---

### Requirement 5: Performance and Scaling

**User Story:** As a capacity planner, I want the graph store sized for multi-hop traversal
queries within latency SLA.

#### Acceptance Criteria

1. MULTI-HOP traversal queries (up to 5 hops for contagion analysis) SHALL complete within 2 seconds p95.
2. READ replicas SHALL be used for agent-driven read-only queries (contagion, blast radius).
3. THE instance class SHALL provide sufficient memory for the graph working set.
4. CLOUDWATCH/Prometheus metrics SHALL monitor query latency, connections, and storage.

---

### Requirement 6: Cost Considerations

**User Story:** As a FinOps stakeholder, I want costs compared between Neptune and self-managed.

#### Acceptance Criteria

1. THE ADR SHALL include a cost comparison: Neptune (per-instance + I/O pricing) vs Neo4j on EKS (compute + storage).
2. DEV environments SHALL use the smallest available instance to minimize cost.
3. THE platform SHALL evaluate whether the graph store is needed in dev (or mock-able).
