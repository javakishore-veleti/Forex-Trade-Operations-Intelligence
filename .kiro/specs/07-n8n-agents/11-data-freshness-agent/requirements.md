# Requirements Document — Data Freshness & Decision-Suitability Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Data Freshness & Decision-Suitability Agent** — a
specialized agent that gates critical processes by verifying that input data is
fresh enough, complete enough, and authoritative enough for the decision at hand.
It acts as a pre-process checkpoint that prevents stale or incomplete data from
contaminating risk calculations, aggregations, or reports.

This agent is implemented as an `AGENT_PLATFORM` workflow export. The
BLOCK/ACCEPT decision comes from a deterministic policy service — the agent
explains the decision and manages the approval flow for overrides.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **DataFreshnessAgent**: The `AGENT_PLATFORM` workflow gating processes on data suitability.
- **FreshnessCheck**: Comparison of dataset timestamp vs maximum-allowed staleness for the downstream process.
- **CompletenessCheck**: Verification that required rows/records are present.
- **AuthoritativenessCheck**: Confirmation that the data source is the designated authority.
- **SuitabilityVerdict**: The deterministic BLOCK/ACCEPT decision from the policy service.
- **FreshnessOverride**: A human-approved exception allowing a stale dataset to proceed.

---

## Requirements

### Requirement 1: Pre-Process Freshness Gate

**User Story:** As a risk operations manager, I want critical processes to be
blocked when input data is stale beyond acceptable limits, so that risk
calculations are never based on outdated information.

#### Acceptance Criteria

1. THE agent SHALL be triggered before a critical process (risk calc, EOD
   aggregation, reporting) starts.
2. THE agent SHALL call `getDatasetFreshness(ds)` for each required dataset.
3. THE agent SHALL compare freshness against the maximum-staleness policy for
   the downstream process.
4. WHEN any dataset exceeds maximum staleness, THE agent SHALL invoke the
   policy service for a BLOCK/ACCEPT verdict.
5. THE agent SHALL produce a per-dataset suitability report.

---

### Requirement 2: Completeness and Authoritativeness

**User Story:** As a data steward, I want completeness and authority verified
alongside freshness, so that partial or wrong-source data is also caught.

#### Acceptance Criteria

1. THE agent SHALL call `getCompleteness(ds)` to verify record counts and
   coverage.
2. THE agent SHALL call `getAuthoritativeness(ds)` to confirm the source is
   the designated authority (not a stale copy).
3. WHEN completeness is below threshold, THE verdict SHALL be BLOCK.
4. WHEN authoritativeness fails, THE verdict SHALL be BLOCK with "wrong
   source" reason.

---

### Requirement 3: HITL Override for Staleness

**User Story:** As an EOD supervisor, I want to override a freshness block
when I determine the staleness is acceptable, so that EOD is not blocked
indefinitely by a known delay.

#### Acceptance Criteria

1. WHEN the verdict is BLOCK, THE agent SHALL present the impact report at a
   HITL gate for override consideration.
2. THE override request SHALL include: which dataset is stale, how stale,
   which downstream processes would use it, and estimated impact.
3. WHEN override is approved, THE agent SHALL record the exception and allow
   the process to proceed.
4. WHEN override is denied, THE process remains blocked.

---

## Risk Classification

- **Inherent risk:** M (can block critical processes; override changes process flow)
- **HITL requirement:** Mandatory for override approvals

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Detection | Deterministic | Freshness/completeness/authority checks |
| Policy | Deterministic | BLOCK/ACCEPT verdict |
| Reasoning | Deep (Opus-class) | Impact explanation, override context |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getDatasetFreshness(ds)` | data-catalog-mcp | L | Timestamp + staleness duration |
| `getCompleteness(ds)` | data-catalog-mcp | L | Record count + coverage % |
| `getAuthoritativeness(ds)` | data-catalog-mcp | L | Source authority verification |
| `getSuitabilityVerdict(ds, process)` | data-policy-mcp | L | Deterministic BLOCK/ACCEPT |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| FRESH-EVAL-01 | All datasets fresh and complete | ACCEPT — process may proceed |
| FRESH-EVAL-02 | Market data stale by 45 min (max: 15 min) | BLOCK — presents override gate |
| FRESH-EVAL-03 | Override approved | Records exception; allows process |
| FRESH-EVAL-04 | Completeness below threshold | BLOCK — "incomplete data" |
| FRESH-EVAL-05 | Wrong source authority | BLOCK — "non-authoritative source" |
| FRESH-EVAL-06 | Override denied | Process remains blocked |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to data catalog and policy |
| 10 — Goal Setting | Process-readiness goal |
| 12 — Human-in-the-Loop | Override approval gate |
| 18 — Guardrails | Prevents stale-data contamination |

---

## Python Sidecar Dependency

None. Freshness/completeness checks are deterministic queries.
