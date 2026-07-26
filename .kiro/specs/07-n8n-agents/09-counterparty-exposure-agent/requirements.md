# Requirements Document — Counterparty Exposure Narrative Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Counterparty Exposure Narrative Agent** — a
specialized agent that produces a live, traceable exposure story for a given
counterparty. It gathers exposure data, limits, concentration, collateral,
and prior-day comparisons from deterministic services and synthesizes a
coherent risk narrative for risk managers.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
compute exposure — it narrates and explains exposure computed by deterministic
services.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **CounterpartyExposureAgent**: The `AGENT_PLATFORM` workflow producing exposure narratives.
- **ExposureStory**: A structured narrative explaining current counterparty exposure with changes, reasons, and materiality.
- **ConcentrationRisk**: Over-exposure to a single currency pair, book, or geography for a counterparty.
- **LimitUtilization**: Current exposure as a percentage of the assigned limit.
- **CollateralCoverage**: Collateral held vs gross exposure for a counterparty.

---

## Requirements

### Requirement 1: Live Exposure Narrative

**User Story:** As a risk manager, I want a coherent story about a
counterparty's current exposure, so that I can quickly assess their risk
posture without reading raw data.

#### Acceptance Criteria

1. THE agent SHALL accept a counterparty identifier and retrieve current
   exposure via `getCounterpartyExposure(cp)`.
2. THE agent SHALL retrieve limit configuration via `getLimits(cp)` and
   compute utilization percentage.
3. THE agent SHALL retrieve prior-day exposure via `getPriorDayExposure(cp)`
   and compute the daily change.
4. THE agent SHALL produce an `ExposureStory` explaining: current position,
   limit utilization, daily change, and materiality assessment.
5. THE agent SHALL highlight any limit approaching breach (>80% utilization).

---

### Requirement 2: Concentration Analysis

**User Story:** As a risk manager, I want to understand where exposure is
concentrated, so that I can identify single-name/pair/region risks.

#### Acceptance Criteria

1. THE agent SHALL retrieve concentration data via `getConcentration(cp)`
   across currency pairs, books, and geographies.
2. THE agent SHALL identify the top-3 concentration risks.
3. THE agent SHALL explain why concentration is material (e.g., "72% of
   exposure is EUR/GBP in book B17").

---

### Requirement 3: Collateral Context

**User Story:** As a credit officer, I want to see collateral coverage in
the context of exposure, so that I can assess net risk.

#### Acceptance Criteria

1. THE agent SHALL retrieve collateral data via `getCollateral(cp)`.
2. THE agent SHALL compute net exposure (gross - collateral).
3. THE agent SHALL flag insufficient collateral coverage.

---

### Requirement 4: Parallel Data Gathering

**User Story:** As a platform architect, I want the agent to gather data in
parallel for performance, since exposure queries span multiple services.

#### Acceptance Criteria

1. THE agent SHALL execute exposure, limits, concentration, collateral, and
   prior-day queries in parallel.
2. THE agent SHALL tolerate individual query failures gracefully, noting
   incomplete data in the narrative.

---

## Risk Classification

- **Inherent risk:** L (read/explain only — no side effects)
- **HITL requirement:** None (read-only agent)

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Reasoning | Deep (Opus-class) | Narrative synthesis, concentration explanation |
| Summarization | Lightweight (Haiku-class) | Compact summary for dashboards |
| Retrieval | Embedding model | Counterparty policy documents (RAG) |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getCounterpartyExposure(cp)` | exposure-mcp | L | Current gross/net exposure |
| `getLimits(cp)` | exposure-mcp | L | Assigned limits and thresholds |
| `getConcentration(cp)` | exposure-mcp | L | Pair/book/geo concentration |
| `getCollateral(cp)` | exposure-mcp | L | Collateral held |
| `getPriorDayExposure(cp)` | exposure-mcp | L | Yesterday's exposure for delta |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| EXP-EVAL-01 | "Tell me about counterparty FX-CP-001 exposure" | Full narrative: position, limits, change, concentration |
| EXP-EVAL-02 | Counterparty near limit breach | Highlights >80% utilization with urgency |
| EXP-EVAL-03 | Counterparty with high concentration | Identifies top-3 concentration risks |
| EXP-EVAL-04 | Counterparty with low collateral | Flags insufficient coverage |
| EXP-EVAL-05 | One data source unavailable | Narrative with gap noted |
| EXP-EVAL-06 | "Compare FX-CP-001 vs yesterday" | Daily delta breakdown |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 3 — Parallelization | Concurrent data gathering across services |
| 5 — Tool Use | MCP tool calls to exposure services |
| 13 — RAG | Policy document retrieval for context |
| 16 — Reasoning | Narrative synthesis with causal explanation |

---

## Python Sidecar Dependency

None. All exposure computation is deterministic (SQL/aggregation service + graph traversal).
