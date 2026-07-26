# Requirements Document — Databricks Lineage & Freshness Impact Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Databricks Lineage & Freshness Impact Agent** — a
specialized agent that assesses the downstream impact of analytics job failures
or schema changes on the trade operations platform. When a data pipeline job
fails or produces late output, this agent traces lineage to determine which
downstream aggregations, reports, or risk processes are affected.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
fix pipeline failures — it assesses impact and gates downstream processes.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **LineageAgent**: The `AGENT_PLATFORM` workflow assessing data pipeline impact.
- **LineageGraph**: The directed graph of table/dataset dependencies from the analytics catalog.
- **JobFailure**: A scheduled analytics job that did not complete successfully.
- **AggregationReadiness**: Whether a downstream aggregation has all required inputs.
- **FreshnessContamination**: When a downstream process uses stale upstream data.

---

## Requirements

### Requirement 1: Downstream Lineage Tracing

**User Story:** As a data operations manager, I want to know what downstream
processes are affected when an analytics job fails, so that I can prevent
contaminated aggregations.

#### Acceptance Criteria

1. THE agent SHALL accept a failed table/job identifier and call
   `getLineageDownstream(table)` to trace dependencies.
2. THE agent SHALL identify all downstream tables, aggregations, and reports
   that depend on the failed source.
3. THE agent SHALL classify affected downstream items by criticality (EOD
   dependency, regulatory report, risk aggregation).
4. THE agent SHALL estimate the time-to-impact: how long until the
   downstream processes would consume stale data.

---

### Requirement 2: Aggregation Readiness Gate

**User Story:** As an EOD operator, I want to verify that all analytics inputs
are ready before triggering aggregation, so that aggregation does not run on
incomplete data.

#### Acceptance Criteria

1. THE agent SHALL call `getJobStatus()` for all upstream jobs feeding the
   aggregation.
2. THE agent SHALL call `getAggregationReadiness(region)` to check
   completeness.
3. WHEN any required input is missing or stale, THE agent SHALL recommend
   blocking aggregation.
4. THE recommendation SHALL be presented at a HITL gate.

---

### Requirement 3: Impact Explanation

**User Story:** As a data steward, I want a clear explanation of the blast
radius of a pipeline failure.

#### Acceptance Criteria

1. THE agent SHALL use the `ReasoningModel` to explain the impact chain:
   which source is late → which intermediate tables → which final reports/calcs.
2. THE explanation SHALL include: affected regions, materiality, and suggested
   remediation timeline.

---

## Risk Classification

- **Inherent risk:** M (can block aggregation processes)
- **HITL requirement:** Mandatory for aggregation block decisions

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Perception | Lightweight (Haiku-class) | Job status parsing |
| Detection | Deterministic | Job failure, freshness check |
| Reasoning | Deep (Opus-class) | Impact chain explanation |
| Policy | Deterministic | Aggregation readiness verdict |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getLineageDownstream(table)` | lineage-mcp | L | Trace downstream dependencies |
| `getJobStatus()` | lineage-mcp | L | Job completion status |
| `getAggregationReadiness(region)` | lineage-mcp | L | Aggregation input status |
| `blockAggregation()` | lineage-mcp | M | Block downstream process (gated) |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| LIN-EVAL-01 | Job "fx-rates-etl" fails | Traces impact to EMEA risk aggregation |
| LIN-EVAL-02 | All jobs healthy | "All inputs ready" |
| LIN-EVAL-03 | Schema change in source table | Identifies affected consumers |
| LIN-EVAL-04 | Block approved | Calls blockAggregation |
| LIN-EVAL-05 | Block denied | Aggregation proceeds (risk accepted) |
| LIN-EVAL-06 | Multiple failures | Prioritized impact list |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to lineage service |
| 10 — Goal Setting | Aggregation readiness goal |
| 12 — Human-in-the-Loop | Block aggregation gate |
| 18 — Guardrails | Prevents contaminated aggregation |

---

## Python Sidecar Dependency

- **databricks-sdk-ingestion**: Python sidecar that ingests job status and
  lineage metadata from the analytics platform into the lineage service.
