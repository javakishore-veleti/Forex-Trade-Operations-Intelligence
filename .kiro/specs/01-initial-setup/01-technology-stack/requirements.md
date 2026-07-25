# Requirements Document — Technology Stack (Single Source of Truth)

## Introduction

This feature is the **single, authoritative registry of every technology choice and version** used anywhere in the Forex Trade Operations Intelligence platform. Every other spec in this repository is written to be **technology-agnostic**: it describes *behavior, capabilities, and requirements* using the **Technology Roles** defined here (e.g. `RELATIONAL_STORE`, `EVENT_STREAM`, `RULES_ENGINE`), and it never hard-codes a product name, framework, or version number.

The purpose is single-point-of-change: to swap a database, bump a framework version, or change a cloud target, a maintainer edits **only this spec**, and every downstream feature spec remains correct because it referred to the role, not the product. This spec owns: programming languages and runtimes, service and frontend frameworks, build tooling, data-store technologies, messaging/streaming, caching, graph, rules engine, analytics, agent platform, agent tool protocol, observability stack, testing technologies, containerization, and cloud target mappings — together with the pinned version of each.

All example identifiers use the synthetic `FX-` prefix (e.g. `FX-000001`). All organization names are fictional. Concrete versions below are the pinned defaults for the reference implementation and are the *only* place they are declared.

---

## Glossary

- **TechnologyStack**: The registry defined by this spec.
- **TechnologyRole**: A stable, product-neutral name for a capability the platform depends on (e.g. `RELATIONAL_STORE`). Downstream specs reference roles, never products.
- **RoleBinding**: The mapping of a `TechnologyRole` to a concrete product and pinned version, declared only in this spec.
- **PinnedVersion**: The explicit version (or version line, e.g. `21`, `3.4.x`) bound to a role; never `latest`.
- **CloudTargetBinding**: The mapping of a `TechnologyRole` to a managed service for a specific cloud (AWS or Azure).
- **AgnosticSpec**: Any other spec in `.kiro/specs/` that references `TechnologyRole`s and contains no product names or version numbers.

---

## Technology Role Registry (normative)

Downstream specs MUST reference the left column. Only this spec declares the right columns.

| Technology Role | Bound Product | Pinned Version |
|---|---|---|
| `SERVICE_LANGUAGE` | Java | `21` (LTS) |
| `SERVICE_FRAMEWORK` | Spring Boot | `3.4.x` |
| `SERVICE_BUILD_TOOL` | Maven | `3.9.x` |
| `SIDECAR_LANGUAGE` | Python | `>=3.11` |
| `SIDECAR_BUILD_BACKEND` | Hatchling | current |
| `FRONTEND_FRAMEWORK` | Angular (standalone) | `19.x` |
| `AGENT_PLATFORM` | n8n | pinned container tag |
| `AGENT_TOOL_PROTOCOL` | Model Context Protocol via Spring AI MCP Server | Spring AI `1.0.x` |
| `RELATIONAL_STORE` | PostgreSQL | `16.x` |
| `DOCUMENT_STORE` | MongoDB | `7.x` |
| `CACHE` | Redis | `7.x` |
| `GRAPH_STORE` | Neo4j | `5.x` |
| `EVENT_STREAM` (local) | Apache Kafka (KRaft mode) | `3.x` |
| `EVENT_STREAM` (cloud, Azure) | Azure Event Hub (Kafka-compatible) | managed |
| `STREAM_PROCESSING` | Kafka Streams | matches `EVENT_STREAM` |
| `RULES_ENGINE` | Drools | `9.x` |
| `ANALYTICS_PLATFORM` | Databricks | managed |
| `OBSERVABILITY_TRACING` | OpenTelemetry | `1.x` |
| `OBSERVABILITY_METRICS` | Prometheus + Grafana | Prometheus `2.x`, Grafana `11.x` |
| `OBSERVABILITY_LOGGING` | Elasticsearch + Logstash + Kibana (ELK) | `8.x` |
| `INTEGRATION_TEST_HARNESS` | Testcontainers | current |
| `WEB_LAYER_TEST` | Spring MockMvc | matches `SERVICE_FRAMEWORK` |
| `PROPERTY_TEST` | jqwik | current |
| `UNIT_TEST_FRAMEWORK` | JUnit 5 (Jupiter) | current |
| `CONTAINER_RUNTIME` | Docker + Docker Compose | current |
| `SERIALIZATION` | Jackson (JSON, ISO-8601 temporals) | matches `SERVICE_FRAMEWORK` |
| `BEAN_VALIDATION` | Jakarta Bean Validation (JSR-380) + Hibernate Validator | current |

---

## Requirements

### Requirement 1: Central Ownership and Agnostic Referencing

**User Story:** As a platform maintainer, I want every technology and version declared in exactly one spec, so that changing a technology is a single-file edit and no feature spec has to be revised.

#### Acceptance Criteria

1. THE `TechnologyStack` SHALL declare a `RoleBinding` for every `TechnologyRole` in the Technology Role Registry, mapping each role to a concrete product and `PinnedVersion`.
2. EVERY other spec in `.kiro/specs/` SHALL be an `AgnosticSpec`: it SHALL reference technologies by `TechnologyRole` name and SHALL NOT contain any product name, framework name, or version number.
3. WHEN a product or version changes, THE `TechnologyStack` SHALL be the only spec requiring modification; downstream `AgnosticSpec`s SHALL remain valid without edits.
4. WHERE a domain concept is inherently tied to a *kind* of technology (e.g. "a cached value may be stale"), THE spec SHALL express it via the role's capability (`CACHE`) rather than the product (`Redis`).
5. IF a downstream spec introduces a technology not present in the Technology Role Registry, THEN THE `TechnologyStack` SHALL be updated to add a new `TechnologyRole` before that spec is considered valid.
6. THE `TechnologyStack` SHALL NOT bind any `TechnologyRole` to the `latest` tag; every binding SHALL carry an explicit `PinnedVersion`.

---

### Requirement 2: Programming Languages and Runtimes

**User Story:** As a developer, I want the language and runtime for each tier declared centrally, so that all services, sidecars, and portals target consistent, pinned language versions.

#### Acceptance Criteria

1. THE `TechnologyStack` SHALL bind `SERVICE_LANGUAGE` to a single pinned language version used by all microservices.
2. THE `TechnologyStack` SHALL bind `SIDECAR_LANGUAGE` to a single pinned language version used by all detection/embedding sidecars.
3. THE `TechnologyStack` SHALL bind `FRONTEND_FRAMEWORK` to a single pinned major version shared by all portals, and SHALL state that all portals use the same major version to prevent drift.
4. THE `TechnologyStack` SHALL state the architectural boundary that microservices/business logic use `SERVICE_LANGUAGE` only, and `SIDECAR_LANGUAGE` is restricted to detection/embedding sidecars — never business logic.

---

### Requirement 3: Service Framework, Build, and Cross-Cutting Libraries

**User Story:** As a service developer, I want the service framework, build tool, serialization, and validation libraries declared once, so that every microservice inherits identical cross-cutting choices.

#### Acceptance Criteria

1. THE `TechnologyStack` SHALL bind `SERVICE_FRAMEWORK`, `SERVICE_BUILD_TOOL`, `SERIALIZATION`, and `BEAN_VALIDATION` to pinned versions.
2. THE `TechnologyStack` SHALL declare that `SERIALIZATION` is configured platform-wide to render temporal values as ISO-8601 strings (not epoch numbers) and monetary values as JSON numbers.
3. THE `TechnologyStack` SHALL declare that `SERVICE_BUILD_TOOL` uses a parent build descriptor that pins shared dependency versions, so that individual modules do not re-declare versions.
4. THE `TechnologyStack` SHALL bind `AGENT_TOOL_PROTOCOL` to the mechanism by which services expose capabilities to agents, and SHALL state that agent tool endpoints are introduced only in the local-deploy phase, not at service-scaffold time.

---

### Requirement 4: Data Store Technologies

**User Story:** As an architect, I want each data-persistence role bound to a concrete store, so that specs can require "a relational store of record" or "a document store" without naming a product.

#### Acceptance Criteria

1. THE `TechnologyStack` SHALL bind `RELATIONAL_STORE`, `DOCUMENT_STORE`, `CACHE`, and `GRAPH_STORE` to pinned products and versions.
2. THE `TechnologyStack` SHALL document the intended use of each store role: `RELATIONAL_STORE` for transactional trade state; `DOCUMENT_STORE` for audit histories and flexible documents; `CACHE` for runtime state, idempotency keys, and short-lived context; `GRAPH_STORE` for dependency, contagion, and relationship graphs.
3. THE `TechnologyStack` SHALL declare that optimistic-locking concurrency (a version field, not row locks) is the platform default for `RELATIONAL_STORE` writes.
4. WHEN a spec needs to distinguish "authoritative" from "cached" data, THE spec SHALL reference `RELATIONAL_STORE` versus `CACHE` roles rather than product names.

---

### Requirement 5: Messaging, Streaming, and Rules Technologies

**User Story:** As an event-driven service developer, I want the event-stream, stream-processing, and rules-engine technologies declared centrally, so that messaging and rule evaluation are consistent and swappable per environment.

#### Acceptance Criteria

1. THE `TechnologyStack` SHALL bind `EVENT_STREAM` for the local environment and SHALL bind the cloud-Azure equivalent as a `CloudTargetBinding` (a Kafka-compatible managed service), so that services use one client contract across environments.
2. THE `TechnologyStack` SHALL bind `STREAM_PROCESSING` to the technology used for continuous, high-volume event detection that emits compact anomaly envelopes (never routing high-volume streams through an agent).
3. THE `TechnologyStack` SHALL bind `RULES_ENGINE` to the deterministic engine used for currency-pair, materiality, and permitted-action policies, and SHALL state that no LLM authors, modifies, or activates rules.
4. THE `TechnologyStack` SHALL bind `ANALYTICS_PLATFORM` for historical, population-level, and backtesting analysis.
5. THE `TechnologyStack` SHALL declare the platform's messaging boundary: exact arithmetic, transactional consistency, and high-volume consumption are performed by `SERVICE_LANGUAGE` services, never by the `AGENT_PLATFORM`.

---

### Requirement 6: Observability and Testing Technologies

**User Story:** As an operator and QA engineer, I want observability and test technologies declared once, so that every service instruments and tests itself the same way.

#### Acceptance Criteria

1. THE `TechnologyStack` SHALL bind `OBSERVABILITY_TRACING`, `OBSERVABILITY_METRICS`, and `OBSERVABILITY_LOGGING` to pinned technologies, and SHALL state that trace context propagates across service and event-stream boundaries with a correlation identifier.
2. THE `TechnologyStack` SHALL bind `UNIT_TEST_FRAMEWORK`, `WEB_LAYER_TEST`, `INTEGRATION_TEST_HARNESS`, and `PROPERTY_TEST` to pinned technologies.
3. WHEN a feature spec requires an integration test, THE spec SHALL reference the `INTEGRATION_TEST_HARNESS` role (which provisions real dependency containers) rather than naming a library.
4. THE `TechnologyStack` SHALL bind `CONTAINER_RUNTIME` used for local orchestration and image builds, and SHALL require pinned image version tags (never `latest`) for every containerized dependency.

---

### Requirement 7: Agent Platform and Tool Protocol

**User Story:** As an agent developer, I want the agent platform and tool protocol declared centrally, so that all agents and all service-exposed tools use one consistent mechanism.

#### Acceptance Criteria

1. THE `TechnologyStack` SHALL bind `AGENT_PLATFORM` as the sole implementation technology for AI agents, and SHALL state that agents are authored as exported workflows of that platform — not as sidecar or service code.
2. THE `TechnologyStack` SHALL bind `AGENT_TOOL_PROTOCOL` as the sole mechanism by which `SERVICE_LANGUAGE` services expose capabilities to the `AGENT_PLATFORM`.
3. THE `TechnologyStack` SHALL state that the `AGENT_PLATFORM` acts as the client of `AGENT_TOOL_PROTOCOL`, and that a human-approval gate is enforced on the platform before any sensitive tool invocation.
4. THE `TechnologyStack` SHALL bind the model tiers used by agents (a fast perception/extraction tier, a mid reasoning tier, and a deep reasoning/planning tier) as roles, so that agent specs reference tiers by role rather than by model name.

---

### Requirement 8: Cloud Target Bindings

**User Story:** As a platform engineer deploying to AWS or Azure, I want each infrastructure role mapped to a managed service per cloud, so that deployment specs can target "the relational store" and resolve to the correct managed service.

#### Acceptance Criteria

1. THE `TechnologyStack` SHALL declare, for each infrastructure `TechnologyRole`, a `CloudTargetBinding` for AWS and for Azure (e.g. `RELATIONAL_STORE` → AWS RDS PostgreSQL / Azure Database for PostgreSQL; `EVENT_STREAM` → AWS MSK / Azure Event Hub; `CACHE` → AWS ElastiCache / Azure Cache for Redis; `DOCUMENT_STORE` → AWS DocumentDB / Azure Cosmos DB Mongo API; `GRAPH_STORE` → AWS Neptune / self-managed or Neo4j Aura; `OBSERVABILITY_LOGGING` → AWS OpenSearch / Azure Monitor + Log Analytics; container orchestration → AWS EKS / Azure AKS).
2. WHEN a deployment spec targets a cloud, THE spec SHALL reference the infrastructure `TechnologyRole` and the target cloud, and SHALL resolve the concrete managed service from the `CloudTargetBinding` table here.
3. THE `TechnologyStack` SHALL state that AWS and Azure deployments are independent targets and that the local `CONTAINER_RUNTIME` stack mirrors the same roles for development.
4. WHERE a cloud lacks a native equivalent for a role (e.g. a managed graph store compatible with the local `GRAPH_STORE`), THE `TechnologyStack` SHALL document the chosen substitute and any client-contract differences.

---

### Requirement 9: Version Pinning and Change Management

**User Story:** As a maintainer, I want a single, auditable place to change versions, so that upgrades are deliberate, reviewable, and applied uniformly.

#### Acceptance Criteria

1. THE `TechnologyStack` SHALL express every `PinnedVersion` explicitly in the Technology Role Registry table.
2. WHEN a `PinnedVersion` is changed, THE `TechnologyStack` SHALL be the only spec edited, and the change SHALL be recorded via an Architecture Decision Record referenced from this spec.
3. THE `TechnologyStack` SHALL NOT permit two components that fill the same `TechnologyRole` to carry different versions (no version drift within a role).
4. IF an `AgnosticSpec` is found to contain a product name or version number, THEN it SHALL be corrected to reference the appropriate `TechnologyRole` before merge.
