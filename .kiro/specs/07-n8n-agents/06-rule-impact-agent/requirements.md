# Requirements Document — Runtime Business Rule Impact Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Runtime Business Rule Impact Agent** — a specialized
agent that detects and explains post-deployment rule firing anomalies. When a
new rule version is deployed and firing patterns deviate significantly from
pre-deployment baselines, this agent identifies the impacted trades, explains
the behavioral change, and proposes a rollback via a human-gated action.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
modify rules — it analyzes firing statistics from deterministic services and
proposes rollback actions that require human approval.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **RuleImpactAgent**: The `AGENT_PLATFORM` workflow detecting post-deploy rule anomalies.
- **FiringRateAnomaly**: A statistically significant deviation in rule firing frequency post-deployment.
- **RuleVersion**: A specific version of a `RULES_ENGINE` rule package (e.g., `v7.14`).
- **BehaviorComparison**: Pre-deployment vs post-deployment firing statistics for a rule set.
- **RollbackGate**: The HITL checkpoint before executing a rule version rollback.
- **ConflictingRule**: A rule whose conditions overlap with another, producing unexpected interactions.

---

## Requirements

### Requirement 1: Firing Pattern Anomaly Detection

**User Story:** As a rules manager, I want to be alerted when a newly deployed
rule version fires at an abnormal rate, so that I can intervene before widespread
impact.

#### Acceptance Criteria

1. THE agent SHALL be triggered by a Python sidecar anomaly envelope indicating
   a firing-rate deviation beyond the configured threshold.
2. THE agent SHALL retrieve firing statistics via `getRuleFiringStats()` for
   the anomalous rule, including pre/post deployment windows.
3. THE agent SHALL identify which currency pairs, books, and regions are
   disproportionately affected.
4. THE agent SHALL quantify the impact: number of trades affected, rejection
   rate change, and exposure impact.
5. THE agent SHALL classify the anomaly as: over-firing, under-firing, or
   pattern shift.

---

### Requirement 2: Pre/Post Deployment Behavior Comparison

**User Story:** As a rules analyst, I want a clear comparison of rule behavior
before and after deployment, so that I can confirm whether the change is
intentional or a defect.

#### Acceptance Criteria

1. THE agent SHALL call `compareRuleBehavior(preVersion, postVersion)` to get
   deterministic comparison metrics.
2. THE comparison SHALL include: firing rate delta, affected population
   characteristics, and new conditions triggered.
3. THE agent SHALL check for conflicting rules via `findConflictingRules()`
   that may cause unintended interactions.
4. THE agent SHALL correlate the deployment timestamp with the behavior shift
   onset to confirm causation.

---

### Requirement 3: Rollback Proposal with HITL Gate

**User Story:** As a risk stakeholder, I want the agent to propose a rule
rollback with a full impact report, requiring my explicit approval before
execution, so that production rules are never auto-reverted.

#### Acceptance Criteria

1. WHEN the anomaly is confirmed and material, THE agent SHALL produce a
   rollback proposal including: target rule version, affected trades count,
   estimated risk if rolled back vs kept.
2. THE agent SHALL call `simulateRule()` with the previous version to
   demonstrate expected behavior restoration.
3. THE rollback proposal SHALL be presented at the HITL gate with full
   impact report.
4. THE agent SHALL NOT execute any rollback without explicit human approval.
5. WHEN approved, THE agent SHALL call `requestRuleRollback()` with the
   approval reference.
6. WHEN denied, THE agent SHALL log the denial and offer monitoring-only mode.

---

### Requirement 4: Embedding-Based Similar Defects

**User Story:** As a rules manager, I want to see similar past rule defects,
so that I can assess if this is a known pattern.

#### Acceptance Criteria

1. THE agent SHALL use embedding retrieval to find similar prior rule anomalies.
2. THE similar cases SHALL include: rule version, impact, resolution taken.
3. THE agent SHALL present at most 3 similar defect cases.

---

## Risk Classification

- **Inherent risk:** H (rollback modifies production rule configuration)
- **HITL requirement:** Mandatory — rollback action gated behind human approval

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Detection | Python sidecar | Firing-rate anomaly detection (statistical) |
| Reasoning | Deep (Opus-class) | Causal analysis, impact explanation |
| Embedding | Embedding model | Similar-defect retrieval |
| Policy | Deterministic (RULES_ENGINE simulation) | Rollback simulation |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getRuleFiringStats()` | rules-engine-mcp | L | Pre/post deployment firing statistics |
| `compareRuleBehavior(preVer, postVer)` | rules-engine-mcp | L | Deterministic behavior comparison |
| `findConflictingRules()` | rules-engine-mcp | L | Detect rule interactions |
| `simulateRule()` | rules-engine-mcp | L | Simulate previous version behavior |
| `requestRuleRollback()` | rules-engine-mcp | H | Execute rollback (gated) |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| RULE-EVAL-01 | Sidecar detects over-firing of rule v7.14 on EUR pairs | Identifies EUR/GBP +28% rejection, proposes rollback |
| RULE-EVAL-02 | Firing rate within normal range | No action; reports "no anomaly detected" |
| RULE-EVAL-03 | Conflicting rules found | Explains interaction between rules, no rollback |
| RULE-EVAL-04 | Rollback approved | Executes `requestRuleRollback()` with approval ref |
| RULE-EVAL-05 | Rollback denied | Logs denial, offers monitoring mode |
| RULE-EVAL-06 | "Has this happened before?" | Returns similar prior rule defects |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP tool calls to rules engine |
| 10 — Goal Setting | Anomaly resolution goal |
| 12 — Human-in-the-Loop | Rollback gate |
| 16 — Reasoning | Causal analysis of firing anomaly |

---

## Python Sidecar Dependency

- **firing-rate-anomaly-detector**: Monitors `RULES_ENGINE` firing statistics
  via metrics, computes baseline deviation, emits anomaly envelope to trigger
  this agent when threshold exceeded.
