# Requirements Document — Business KPI Guard Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Business KPI Guard Agent** — a specialized agent
that monitors key business performance indicators (booking rates, rejection
rates, processing throughput) and triggers investigation only when statistical
anomalies are detected. The LLM is invoked only after anomaly detection, not
for continuous monitoring.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
fix KPI issues — it detects, explains, and correlates anomalies.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **KPIGuardAgent**: The `AGENT_PLATFORM` workflow monitoring business KPIs.
- **BusinessKPI**: A measurable metric such as booking rate, rejection rate, processing throughput per region.
- **SeasonalBaseline**: The expected KPI value adjusted for time-of-day, day-of-week, and business calendar.
- **KPIAnomaly**: A statistically significant deviation from the seasonal baseline.
- **AnomalyEnvelope**: The compact payload from the Python sidecar indicating a KPI breach.

---

## Requirements

### Requirement 1: Anomaly-Triggered Investigation

**User Story:** As an operations manager, I want to be alerted only when
business KPIs deviate significantly from their expected baseline, so that I
don't get overwhelmed by noise.

#### Acceptance Criteria

1. THE agent SHALL be triggered by an `AnomalyEnvelope` from the Python
   sidecar when a KPI breaches its seasonal baseline.
2. THE agent SHALL NOT perform continuous polling — detection is delegated to
   the statistical sidecar.
3. THE agent SHALL retrieve current KPI values via `getBusinessKpis(region)`.
4. THE agent SHALL retrieve the seasonal baseline via `getSeasonalBaseline(kpi)`.
5. THE agent SHALL confirm the anomaly by comparing current vs baseline
   (sidecar's detection is the trigger, agent verifies with fresh data).

---

### Requirement 2: Root Cause Correlation

**User Story:** As an operations analyst, I want the agent to identify what
changed that caused the KPI deviation, so that I can act on the root cause.

#### Acceptance Criteria

1. THE agent SHALL retrieve reject breakdown via `getRejectBreakdown()` when
   the anomaly relates to rejection rates.
2. THE agent SHALL correlate the anomaly with recent changes (deploys, rule
   updates, config changes) — this may invoke the Change Correlation Agent.
3. THE agent SHALL identify the affected scope: region, book, currency pair.
4. THE agent SHALL explain the probable cause using the `ReasoningModel`.

---

### Requirement 3: Calendar-Aware Analysis

**User Story:** As an operations manager, I want KPI analysis to account for
business calendar effects (holidays, early closes), so that normal calendar-
driven dips are not flagged as anomalies.

#### Acceptance Criteria

1. THE seasonal baseline SHALL incorporate business calendar (holidays,
   half-days, regional close patterns).
2. THE agent SHALL note when a deviation coincides with a calendar event.
3. THE agent SHALL suppress false positives from known calendar effects.

---

## Risk Classification

- **Inherent risk:** L (read/explain only — no operational changes)
- **HITL requirement:** None (advisory agent)

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Detection | Python sidecar | Time-series anomaly + seasonal baseline |
| Reasoning | Deep (Opus-class) | Root cause correlation, explanation |
| Memory | Episodic | Similar past anomalies |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getBusinessKpis(region)` | kpi-mcp | L | Current KPI values |
| `getSeasonalBaseline(kpi)` | kpi-mcp | L | Expected baseline |
| `getRejectBreakdown()` | kpi-mcp | L | Rejection reason breakdown |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| KPI-EVAL-01 | APAC booking rate 41% below norm | Identifies anomaly, explains probable cause |
| KPI-EVAL-02 | Normal holiday dip | Suppresses — "calendar-driven, not anomalous" |
| KPI-EVAL-03 | Rejection rate spike for EUR pairs | Correlates with recent rule deploy v7.14 |
| KPI-EVAL-04 | All KPIs within baseline | "No anomalies detected" (not triggered) |
| KPI-EVAL-05 | "Why is APAC booking low?" (on-demand) | Retrieves and explains without sidecar trigger |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to KPI service |
| 10 — Goal Setting | KPI normalcy goal |
| 16 — Reasoning | Root cause explanation |
| 17 — Evaluation | KPI metric monitoring |

---

## Python Sidecar Dependency

- **kpi-anomaly-detector**: Business-calendar-aware time-series anomaly
  detector. Monitors per-region KPIs, computes seasonal baseline, emits
  `AnomalyEnvelope` when threshold breached. The LLM is never used for
  continuous monitoring.
