# Requirements Document — Contagion Analysis Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Contagion Analysis Agent** — a specialized agent
that calculates the business blast radius of a failure (service, feed, or
counterparty) using graph traversal. It answers "what else is affected?"

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
remediate — it maps the contagion path and quantifies the business blast
radius for informed decision-making.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **ContagionAgent**: The `AGENT_PLATFORM` workflow computing blast radius.
- **BlastRadius**: The set of trades, books, regions, and aggregations affected by a failure.
- **ContagionPath**: The graph traversal path from failure source to affected entities.
- **DependencyGraph**: The `GRAPH_STORE` containing relationships (trade→pair→counterparty→book→region→rule→feed→service→topic).
- **ImpactSeverity**: Classification of downstream impact (CRITICAL, HIGH, MEDIUM, LOW).

---

## Requirements

### Requirement 1: Failure Source Blast Radius

**User Story:** As an operations manager, I want to know the full blast radius
of a service or feed failure, so that I can scope the incident.

#### Acceptance Criteria

1. THE agent SHALL accept a failure source (service, feed, counterparty, or
   pair) and compute affected entities via graph traversal.
2. THE agent SHALL call `findTradeDependencies()` to find trades depending
   on the failing entity.
3. THE agent SHALL call `findAffectedBooks()` to determine book-level impact.
4. THE agent SHALL call `findDownstreamAggregations()` for report/EOD impact.
5. THE agent SHALL quantify the blast radius: trade count, notional, regions.

---

### Requirement 2: Shared Dependency Detection

**User Story:** As a risk analyst, I want shared market-data dependencies
identified, so that I understand hidden concentration risk.

#### Acceptance Criteria

1. THE agent SHALL call `findSharedMarketDataDependencies()` to detect pairs
   sharing feed or rate sources.
2. THE agent SHALL highlight when a single feed failure affects multiple pairs.
3. THE agent SHALL relate feed-level contagion to book-level impact.

---

### Requirement 3: Business Impact Quantification

**User Story:** As a senior operations lead, I want business impact quantified
in business terms (trades, notional, regions), so that I can escalate
appropriately.

#### Acceptance Criteria

1. THE agent SHALL call `calculateBusinessBlastRadius()` to produce aggregate
   impact metrics.
2. THE agent SHALL classify impact by severity: CRITICAL (>1000 trades or
   >$1B notional), HIGH, MEDIUM, LOW.
3. THE agent SHALL produce a readable narrative summarizing the contagion path
   and business impact.
4. THE agent SHALL include regional scope (which regions affected).

---

## Risk Classification

- **Inherent risk:** L (read-only graph traversal — no side effects)
- **HITL requirement:** None (read-only analysis)

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Planning | Deep (Opus-class) | Traversal strategy for complex graphs |
| Reasoning | Deep (Opus-class) | Impact narrative synthesis |
| Detection | Deterministic (graph queries) | Cypher templates for traversal |
| Memory | Episodic | Prior blast-radius analyses |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `findTradeDependencies()` | graph-mcp | L | Trades depending on entity |
| `findAffectedBooks()` | graph-mcp | L | Book-level impact |
| `findDownstreamAggregations()` | graph-mcp | L | Report/EOD impact |
| `findSharedMarketDataDependencies()` | graph-mcp | L | Shared feed dependencies |
| `calculateBusinessBlastRadius()` | graph-mcp | L | Aggregate business metrics |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| CONT-EVAL-01 | "Market-data feed X fails" | Blast radius: pairs, trades, books, regions |
| CONT-EVAL-02 | "Counterparty FX-CP-003 defaults" | Affected trades, settlement at risk |
| CONT-EVAL-03 | "Service enrichment-svc down" | Downstream trade processing blocked |
| CONT-EVAL-04 | Shared feed: 5 pairs on one source | Concentration risk highlighted |
| CONT-EVAL-05 | Minor failure, 3 trades | LOW severity, contained |
| CONT-EVAL-06 | Cascading failure | CRITICAL severity, multi-region |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls via Cypher templates to graph store |
| 6 — Planning | Traversal strategy for multi-hop queries |
| 16 — Reasoning | Causal narrative of contagion path |
| 18 — Guardrails | No free-form Cypher — templates only |

---

## Python Sidecar Dependency

- None — all graph traversal is via deterministic Cypher templates exposed
  through the graph-mcp tool boundary.
