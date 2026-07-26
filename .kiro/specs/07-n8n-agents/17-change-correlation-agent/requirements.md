# Requirements Document — Runtime Change Correlation Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Runtime Change Correlation Agent** — a specialized
agent that identifies correlations between infrastructure/configuration changes
and business behavior shifts. When a business outcome changes (e.g., rejection
rate spike), this agent looks back at recent deployments, rule changes, config
updates, and schema changes to find the probable cause.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
make changes — it explains correlations for investigation.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **ChangeCorrelationAgent**: The `AGENT_PLATFORM` workflow correlating changes to outcomes.
- **ChangeEvent**: A deployment, config update, rule publish, schema change, or feature flag toggle.
- **BehaviorShift**: A measurable change in business outcomes (rejection rate, latency, error rate).
- **CorrelationWindow**: The time window around a behavior shift to search for causal changes.
- **ChangeGraph**: The dependency graph linking change events to affected services and outcomes.

---

## Requirements

### Requirement 1: Change Discovery

**User Story:** As an operations analyst, I want to see all changes that
occurred in the window before a business behavior shift, so that I can
identify the probable cause.

#### Acceptance Criteria

1. THE agent SHALL accept a behavior shift timestamp and call
   `getRecentChanges(window)` to retrieve all changes in the correlation window.
2. THE changes SHALL include: K8s deployments, GitOps events, config audit
   entries, rule deployments, feature flag toggles, schema registry updates.
3. THE agent SHALL rank changes by proximity to the behavior shift onset.
4. THE agent SHALL filter irrelevant changes (e.g., unrelated services).

---

### Requirement 2: Causal Correlation

**User Story:** As a platform engineer, I want the agent to explain the likely
causal chain from a specific change to the observed behavior shift.

#### Acceptance Criteria

1. THE agent SHALL call `correlateChangeToOutcome()` to get deterministic
   correlation metrics (timing match, service overlap, affected path).
2. THE agent SHALL call `getChangeGraph(entity)` to visualize the dependency
   path from change to affected outcome.
3. THE agent SHALL use the `ReasoningModel` to explain the causal hypothesis.
4. THE explanation SHALL include confidence level and alternative hypotheses.

---

### Requirement 3: Memory and Pattern Recognition

**User Story:** As an SRE, I want the agent to recognize if this
change→behavior pattern has occurred before, so that known issues are
resolved faster.

#### Acceptance Criteria

1. THE agent SHALL check episodic memory for similar change→outcome patterns.
2. WHEN a match is found, THE agent SHALL reference the prior incident and
   its resolution.
3. THE agent SHALL learn from prior correlations to improve future ranking.

---

## Risk Classification

- **Inherent risk:** L (read/explain only — no changes or rollbacks)
- **HITL requirement:** None (advisory agent)

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Perception | Lightweight (Haiku-class) | Change event normalization |
| Detection | Python sidecar | Behavior shift detection |
| Reasoning | Deep (Opus-class) | Causal hypothesis generation |
| Memory | Episodic | Prior change→outcome patterns |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getRecentChanges(window)` | change-mcp | L | All changes in window |
| `correlateChangeToOutcome()` | change-mcp | L | Correlation metrics |
| `getChangeGraph(entity)` | change-mcp | L | Dependency path visualization |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| CHG-EVAL-01 | Rejection rate spike at 14:05 | Identifies rule pkg v7.14 deployed at 14:01 |
| CHG-EVAL-02 | Latency increase after deploy | Correlates K8s deployment to service |
| CHG-EVAL-03 | No changes in window | "No correlated changes found" |
| CHG-EVAL-04 | Multiple changes in window | Ranks by probability |
| CHG-EVAL-05 | Known pattern from memory | References prior incident |
| CHG-EVAL-06 | Schema change + behavior shift | Explains schema→consumer→outcome chain |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to change tracking service |
| 8 — Memory Management | Prior change→outcome patterns |
| 13 — RAG | Similar incident retrieval |
| 16 — Reasoning | Causal hypothesis generation |

---

## Python Sidecar Dependency

- **behavior-shift-detector**: Detects business behavior shifts in metrics
  streams. Emits shift envelope with onset timestamp and affected metric.
