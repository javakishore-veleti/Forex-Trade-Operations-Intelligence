# Requirements Document — End-of-Day Risk Readiness Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **End-of-Day Risk Readiness Agent** — a multi-agent
workflow that assesses regional readiness for the EOD risk consolidation
process. It uses regional sub-agents (one per region) coordinated by a global
supervisor to produce a unified go/no-go readiness map.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
execute EOD processing — it assesses readiness and gates the process start
behind human approval when blockers exist.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **EODReadinessAgent**: The global supervisor `AGENT_PLATFORM` workflow.
- **RegionalSubAgent**: A lightweight sub-workflow assessing one region's readiness.
- **ReadinessMap**: The aggregate status across all regions (READY/BLOCKED/WARNING).
- **GoNoGo**: The final decision: proceed with global consolidation or hold.
- **Blocker**: A condition that prevents a region from being marked ready.
- **Exception**: A known issue that can be approved for pass-through.

---

## Requirements

### Requirement 1: Regional Readiness Assessment

**User Story:** As an EOD operator, I want each region's readiness assessed
independently, so that I can see which regions are ready and which are blocked.

#### Acceptance Criteria

1. THE agent SHALL spawn a regional sub-agent for each active region (APAC,
   EMEA, AMER).
2. EACH regional sub-agent SHALL call `getRegionalCloseStatus(region)` to
   determine close state.
3. EACH regional sub-agent SHALL call `getUnprocessedTradeCount(region)` to
   check pending trades.
4. EACH regional sub-agent SHALL call `getLateTradeMateriality(region)` to
   assess if late trades are material.
5. EACH regional sub-agent SHALL call `getMarketDataReadiness(region)` to
   verify data availability.
6. EACH regional sub-agent SHALL call `getBranchCompletionStatus(region)` for
   branch-level completion.
7. EACH regional sub-agent SHALL produce a regional status: READY, WARNING,
   or BLOCKED with reasons.

---

### Requirement 2: Global Readiness Consolidation

**User Story:** As an EOD supervisor, I want a unified view of all regions
with a clear go/no-go recommendation.

#### Acceptance Criteria

1. THE global supervisor SHALL aggregate all regional statuses into a
   `ReadinessMap`.
2. THE supervisor SHALL use the `ReasoningModel` to synthesize a go/no-go
   recommendation considering cross-regional dependencies.
3. THE supervisor SHALL identify the critical path: which blocker must be
   resolved first.
4. WHEN all regions are READY, THE supervisor SHALL recommend "GO".
5. WHEN any region is BLOCKED, THE supervisor SHALL recommend "NO-GO" with
   required actions.

---

### Requirement 3: HITL Exception Approval

**User Story:** As an EOD supervisor, I want to approve exceptions for
non-material blockers, so that EOD can proceed when blockers are tolerable.

#### Acceptance Criteria

1. WHEN a region is BLOCKED by a non-material issue, THE agent SHALL present
   the exception for human approval.
2. THE HITL gate SHALL include: blocker description, affected trades, exposure
   impact, and risk classification.
3. WHEN the exception is approved, THE agent SHALL update the `ReadinessMap`
   and re-evaluate go/no-go.
4. WHEN denied, THE region remains BLOCKED.

---

### Requirement 4: Gated Consolidation Start

**User Story:** As a risk stakeholder, I want global consolidation to start
only with my explicit approval.

#### Acceptance Criteria

1. WHEN go/no-go is "GO", THE agent SHALL present the final readiness map at
   a HITL gate before triggering `startGlobalConsolidation()`.
2. THE agent SHALL NOT auto-start consolidation.
3. WHEN approved, THE agent SHALL call `startGlobalConsolidation()`.
4. WHEN denied, THE agent SHALL hold and await re-assessment.

---

## Risk Classification

- **Inherent risk:** M (can block/hold EOD process, gated consolidation start)
- **HITL requirement:** Mandatory for exception approval and consolidation start

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Regional agents | Mid-tier (Sonnet-class) | Per-region assessment |
| Global supervisor | Deep (Opus-class) | Cross-regional synthesis, go/no-go |
| Detection | Deterministic | Unprocessed counts, close status |
| Policy | Deterministic | Materiality classification |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getRegionalCloseStatus(region)` | eod-processing-mcp | L | Region close state |
| `getUnprocessedTradeCount(region)` | eod-processing-mcp | L | Pending trade count |
| `getLateTradeMateriality(region)` | eod-processing-mcp | L | Late trade assessment |
| `getMarketDataReadiness(region)` | market-data-mcp | L | Data availability |
| `getBranchCompletionStatus(region)` | eod-processing-mcp | L | Branch completion |
| `getRiskAggregationStatus(region)` | risk-calculation-mcp | L | Aggregation state |
| `startRegionalRerun(region)` | eod-processing-mcp | M | Re-run region (gated) |
| `approveException(region)` | eod-processing-mcp | M | Exception approval (gated) |
| `startGlobalConsolidation()` | eod-processing-mcp | M | Start consolidation (gated) |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| EOD-EVAL-01 | All regions ready | GO recommendation, HITL gate for consolidation |
| EOD-EVAL-02 | EMEA blocked by unprocessed trades | NO-GO; identifies blocker |
| EOD-EVAL-03 | APAC has non-material exception | Presents exception for approval |
| EOD-EVAL-04 | Exception approved | Re-evaluates; may change to GO |
| EOD-EVAL-05 | Consolidation approved | Executes `startGlobalConsolidation()` |
| EOD-EVAL-06 | Market data stale for AMER | AMER marked WARNING/BLOCKED |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 3 — Parallelization | Regional sub-agents run concurrently |
| 6 — Planning | Critical path identification |
| 7 — Multi-Agent | Regional sub-agents + global supervisor |
| 10 — Goal Setting | EOD completion goal |
| 12 — Human-in-the-Loop | Exception approval + consolidation gate |

---

## Python Sidecar Dependency

None. All readiness checks are deterministic service queries.
