# Requirements Document — Service Genome Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Service Genome Agent** — a specialized agent that
maintains knowledge profiles for each microservice and predicts fragility by
analyzing dependencies, change frequency, and runtime patterns.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
modify services — it builds and queries service profiles to inform change
planning and incident investigation.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **ServiceGenomeAgent**: The `AGENT_PLATFORM` workflow profiling services.
- **ServiceProfile**: A structured knowledge record of a service's dependencies, APIs, topics, and runtime characteristics.
- **FragilityScore**: A computed metric predicting likelihood of failure given a change.
- **ChangeImpactPrediction**: Analysis of which services are most at risk from a planned change.
- **DependencyMap**: The graph of service-to-service, service-to-topic, and service-to-data relationships.

---

## Requirements

### Requirement 1: Service Knowledge Profiling

**User Story:** As a platform architect, I want automated knowledge profiles
for each service, so that I understand service characteristics at a glance.

#### Acceptance Criteria

1. THE agent SHALL call `getServiceProfile(svc)` to retrieve runtime
   characteristics: endpoints, topics consumed/produced, dependencies.
2. THE agent SHALL call `getDependencies(svc)` to map upstream and downstream
   service relationships.
3. THE agent SHALL build profiles from: OpenAPI specs, runtime logs, Kafka
   topics, deployment manifests, and call patterns.
4. THE agent SHALL maintain profile freshness (staleness indicator).

---

### Requirement 2: Fragility Prediction

**User Story:** As a release engineer, I want to know which services are
most fragile before deploying changes, so that I can add safeguards.

#### Acceptance Criteria

1. THE agent SHALL call `predictFragility(svc)` using the Python sidecar
   architecture-pattern classifier.
2. THE fragility score SHALL consider: change frequency, dependency count,
   blast radius, incident history, test coverage.
3. THE agent SHALL rank services by fragility for a given change scope.
4. THE agent SHALL explain fragility drivers for each service.

---

### Requirement 3: Change Consumer Identification

**User Story:** As a developer, I want to know who consumes my service
before making changes, so that I can coordinate.

#### Acceptance Criteria

1. THE agent SHALL call `findConsumersOfChange()` to identify services
   that depend on the changing component.
2. THE agent SHALL produce a consumer impact map with communication type
   (sync API, async topic, shared DB).
3. THE agent SHALL recommend coordination actions for high-risk consumers.

---

## Risk Classification

- **Inherent risk:** L (read-only profile and prediction — no side effects)
- **HITL requirement:** None (advisory only)

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Perception | Lightweight (Haiku-class) | Spec/manifest parsing |
| Detection | Python sidecar | Architecture-pattern classifier, semantic diff |
| Reasoning | Deep (Opus-class) | Fragility explanation, change impact |
| Memory | Graph (Neo4j) | Service knowledge graph |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getServiceProfile(svc)` | platform-mcp | L | Service runtime characteristics |
| `getDependencies(svc)` | graph-mcp | L | Upstream/downstream dependencies |
| `predictFragility(svc)` | platform-mcp | L | Python sidecar fragility score |
| `findConsumersOfChange()` | graph-mcp | L | Consumer identification |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| GENOME-EVAL-01 | "Profile risk-calculation-service" | Full profile with deps, topics, endpoints |
| GENOME-EVAL-02 | "Which service is most fragile?" | Ranked fragility list with drivers |
| GENOME-EVAL-03 | "Who consumes trade-lifecycle API?" | Consumer map with types |
| GENOME-EVAL-04 | "Impact of changing enrichment-svc?" | Downstream services + topics affected |
| GENOME-EVAL-05 | Profile request for unknown service | "Service not found in registry" |
| GENOME-EVAL-06 | Service with no dependencies | Profile with independence noted |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to platform and graph services |
| 14 — Inter-Agent | Provides context to other agents |
| 16 — Reasoning | Fragility explanation |
| 20 — Exploration | Pattern discovery in service behavior |

---

## Python Sidecar Dependency

- **architecture-pattern-classifier**: Classifies service architecture patterns
  (stateful/stateless, sync/async, fan-in/fan-out) from runtime telemetry.
  Computes fragility score from multi-factor model.
