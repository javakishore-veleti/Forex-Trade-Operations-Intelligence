# Requirements Document — Market-Data Feed Staleness Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Market-Data Feed Staleness Agent** — a specialized
agent that detects stale market-data feeds, crossed quotes, and tick gaps,
then gates downstream risk calculations to prevent contaminated results.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
compute rates — it detects feed staleness and gates risk processes via
human-approved blocking actions.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **MarketDataStalenessAgent**: The `AGENT_PLATFORM` workflow detecting feed staleness.
- **TickGap**: A gap in the expected tick stream for a currency pair exceeding threshold.
- **CrossedQuote**: A condition where the bid price exceeds the ask price.
- **StalePair**: A currency pair whose last tick exceeds maximum-age policy.
- **RiskCalcBlock**: A gated action that blocks risk calculation for contaminated pairs.

---

## Requirements

### Requirement 1: Feed Freshness Detection

**User Story:** As a risk manager, I want stale market-data feeds detected
immediately, so that risk calculations use only fresh data.

#### Acceptance Criteria

1. THE agent SHALL call `getFeedFreshness(pair)` to check last-tick age
   for each monitored currency pair.
2. THE agent SHALL call `getStalePairs()` to retrieve all pairs exceeding
   freshness thresholds.
3. THE agent SHALL detect tick gaps where expected ticks are missing beyond
   a configurable threshold.
4. THE agent SHALL distinguish between market-closed (expected silence) and
   stale feed (unexpected silence) using business calendar context.

---

### Requirement 2: Crossed-Quote Detection

**User Story:** As a pricing analyst, I want crossed quotes detected and
flagged, so that invalid prices do not propagate to risk calculations.

#### Acceptance Criteria

1. THE agent SHALL call `detectCrossedQuote()` to identify pairs with
   bid > ask anomalies.
2. THE agent SHALL correlate crossed quotes with feed source to identify
   problematic providers.
3. THE agent SHALL report duration of crossed state.

---

### Requirement 3: Risk Calculation Blocking Gate

**User Story:** As a risk operations lead, I want risk calculations blocked
for affected currency pairs, so that stale data does not contaminate results.

#### Acceptance Criteria

1. THE agent SHALL call `getDownstreamRiskDependency(pair)` to list trades
   and risk processes depending on the stale pair.
2. WHEN a pair is stale or crossed, THE agent SHALL propose blocking risk
   calculations for that pair at a HITL gate.
3. WHEN blocking is approved, THE agent SHALL call `blockRiskCalc(pair)`.
4. THE agent SHALL report which trades and EOD processes are affected by
   the block.

---

## Risk Classification

- **Inherent risk:** M (blocks risk calculation process for affected pairs)
- **HITL requirement:** Required for blockRiskCalc action

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Detection | Python sidecar | Tick-gap statistics, crossed-quote detection |
| Reasoning | Deep (Opus-class) | Impact analysis, block recommendation |
| Policy | Deterministic | Freshness threshold enforcement |
| Memory | Episodic | Prior staleness events |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getFeedFreshness(pair)` | market-data-mcp | L | Last-tick age per pair |
| `detectCrossedQuote()` | market-data-mcp | L | Bid > ask anomalies |
| `getStalePairs()` | market-data-mcp | L | All pairs exceeding threshold |
| `getDownstreamRiskDependency(pair)` | risk-calculation-mcp | L | Trades/processes using pair |
| `blockRiskCalc(pair)` | risk-calculation-mcp | M | Block risk calc for pair (gated) |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| MKT-EVAL-01 | EUR/USD stale 15min during London | Detected, block proposed |
| MKT-EVAL-02 | Block approved | Calls blockRiskCalc(EUR/USD) |
| MKT-EVAL-03 | Crossed quote on GBP/JPY | Flags, shows affected trades |
| MKT-EVAL-04 | Pair silent during market close | Ignored (expected silence) |
| MKT-EVAL-05 | All feeds fresh | "All feeds within freshness SLA" |
| MKT-EVAL-06 | Multiple pairs stale (same source) | Groups by provider, escalates |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to market-data and risk services |
| 10 — Goal Setting | Fresh data for all risk calculations |
| 12 — Human-in-the-Loop | Block approval gate |
| 18 — Guardrails | Prevent contaminated risk results |

---

## Python Sidecar Dependency

- **tick-gap-detector**: Monitors tick frequency per pair, emits staleness
  envelope when gap exceeds pair-specific threshold. Calendar-aware.
