# Requirements Document — State Divergence Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **State Divergence Agent** — a specialized agent
that detects cross-system state divergences for trades and proposes
reconciliation actions with human approval. It compares canonical state
across `RELATIONAL_STORE`, `DOCUMENT_STORE`, `CACHE`, and `EVENT_STREAM`.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
determine authoritative state — it identifies divergences and proposes
reconciliation via human-gated tools.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **StateDivergenceAgent**: The `AGENT_PLATFORM` workflow detecting cross-system drift.
- **CanonicalState**: The authoritative state as determined by the deterministic StateReconciliationService.
- **StateVector**: The set of per-system states for a trade (Postgres, MongoDB, Redis, Kafka, Databricks).
- **Divergence**: A mismatch between system states that violates invariants.
- **ReconciliationAction**: A gated action to restore state consistency.

---

## Requirements

### Requirement 1: Cross-System State Comparison

**User Story:** As a platform operator, I want trade state compared across
all storage systems, so that divergences are detected early.

#### Acceptance Criteria

1. THE agent SHALL call `queryTradeState()` to get trade state from
   `RELATIONAL_STORE`.
2. THE agent SHALL call `getTradeDocument()` to get state from
   `DOCUMENT_STORE`.
3. THE agent SHALL call `getCachedTradeState()` to get state from `CACHE`.
4. THE agent SHALL call `getLatestDomainEvent()` to get last event from
   `EVENT_STREAM`.
5. THE agent SHALL call `getAnalyticsTradeState()` to get state from
   analytics layer.
6. THE agent SHALL call `evaluateCanonicalState()` to determine the
   authoritative state via the deterministic reconciliation service.

---

### Requirement 2: Divergence Classification

**User Story:** As a data steward, I want divergences classified by severity
and root cause, so that I can prioritize resolution.

#### Acceptance Criteria

1. THE agent SHALL classify divergences: STALE_CACHE, EVENT_LAG,
   WRITE_FAILURE, SCHEMA_MISMATCH, UNKNOWN.
2. THE agent SHALL quantify staleness (how old is the divergent state).
3. THE agent SHALL identify which system is the stale/incorrect one.
4. THE agent SHALL report business impact (e.g., risk using stale data,
   wrong settlement instruction exposed).

---

### Requirement 3: Reconciliation Proposal

**User Story:** As an operations manager, I want reconciliation actions
proposed with my approval, so that state consistency is restored safely.

#### Acceptance Criteria

1. WHEN divergence is detected, THE agent SHALL propose reconciliation
   at a HITL gate with: stale system, correct value, proposed action.
2. THE proposal SHALL include the permitted actions from
   `evaluateCanonicalState()` response.
3. WHEN approved, THE agent SHALL call `startReconciliation()`.
4. THE agent SHALL NOT auto-reconcile without human approval.

---

## Risk Classification

- **Inherent risk:** M (reconciliation modifies system state)
- **HITL requirement:** Required for reconciliation actions

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Detection | Deterministic (StateReconciliationService) | Canonical state calculation |
| Reasoning | Deep (Opus-class) | Divergence explanation, impact assessment |
| Policy | Deterministic | Permitted reconciliation actions |
| Memory | Episodic | Prior divergence events |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `queryTradeState()` | trade-lifecycle-mcp | L | Postgres state |
| `getTradeDocument()` | trade-lifecycle-mcp | L | MongoDB state |
| `getCachedTradeState()` | trade-lifecycle-mcp | L | Redis state |
| `getLatestDomainEvent()` | streaming-mcp | L | Last Kafka event |
| `getAnalyticsTradeState()` | analytics-mcp | L | Databricks state |
| `evaluateCanonicalState()` | state-reconciliation-mcp | L | Deterministic canonical state |
| `startReconciliation()` | state-reconciliation-mcp | M | Start reconciliation (gated) |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| STATE-EVAL-01 | Redis stale vs Postgres | Cache divergence detected |
| STATE-EVAL-02 | Reconciliation approved | Calls startReconciliation |
| STATE-EVAL-03 | All systems consistent | "No divergence detected" |
| STATE-EVAL-04 | Event lag (Kafka behind) | EVENT_LAG classified |
| STATE-EVAL-05 | Write failure (Mongo) | WRITE_FAILURE + impact |
| STATE-EVAL-06 | Scheduled sweep | Multiple trades assessed |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 3 — Parallelization | Parallel state fetches from 5 systems |
| 5 — Tool Use | MCP calls to lifecycle, streaming, analytics |
| 12 — Human-in-the-Loop | Reconciliation approval gate |
| 19 — Prioritization | Divergence severity ranking |

---

## Python Sidecar Dependency

- None — state divergence detection is fully deterministic via the
  StateReconciliationService. No ML or statistical analysis required.
