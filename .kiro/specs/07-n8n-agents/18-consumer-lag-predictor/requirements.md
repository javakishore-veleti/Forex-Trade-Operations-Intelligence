# Requirements Document — Consumer-Lag SLA Predictor Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Consumer-Lag SLA Predictor Agent** — a specialized
agent that forecasts whether event consumers will complete processing before
their regional cutoff deadlines. When the forecast indicates a miss, the agent
proposes scaling actions (gated by human approval) or processing deferral.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
auto-scale — scaling proposals require explicit human approval.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **ConsumerLagPredictor**: The `AGENT_PLATFORM` workflow forecasting consumer completion.
- **ConsumerLag**: The offset gap between produced events and consumed events for a consumer group.
- **CompletionForecast**: The estimated time to fully consume the current backlog at current throughput.
- **PartitionSkew**: Uneven distribution of messages across partitions causing hot spots.
- **CutoffDeadline**: The regional business cutoff by which processing must complete.
- **ScalingProposal**: A recommended replica count change to meet the deadline.

---

## Requirements

### Requirement 1: Completion Time Forecasting

**User Story:** As an operations manager, I want to know if consumers will
finish processing before the cutoff, so that I can intervene proactively.

#### Acceptance Criteria

1. THE agent SHALL be triggered by a lag threshold breach (sidecar) or
   scheduled pre-cutoff check.
2. THE agent SHALL call `getLagByPartition()` to get per-partition lag.
3. THE agent SHALL call `getCompletionForecast()` (Python ML sidecar) to
   predict completion time based on current throughput and backlog.
4. THE agent SHALL compare forecast completion vs regional cutoff deadline.
5. THE agent SHALL identify partition skew via `getHotPartitionKeys()`.

---

### Requirement 2: Scaling Proposal with HITL Gate

**User Story:** As a platform engineer, I want scaling recommendations when
consumers won't meet the deadline, with human approval before execution.

#### Acceptance Criteria

1. WHEN the forecast indicates a cutoff miss, THE agent SHALL propose a
   scaling action (e.g., "scale from 18 to 26 replicas").
2. THE proposal SHALL include: current replicas, proposed replicas, estimated
   new completion time, cost impact estimate.
3. THE proposal SHALL be presented at a HITL gate.
4. WHEN approved, THE agent SHALL call `requestReplicaScale(from, to)`.
5. WHEN denied, THE agent SHALL propose alternative: defer reconciliation
   to next window.
6. THE agent SHALL NOT auto-scale without human approval.

---

### Requirement 3: Hot Partition Detection

**User Story:** As a platform engineer, I want to know if lag is caused by
partition skew rather than overall throughput, so that scaling may not solve
the problem.

#### Acceptance Criteria

1. THE agent SHALL identify partitions with disproportionate lag.
2. THE agent SHALL identify the hot keys causing skew.
3. WHEN skew is the primary cause, THE agent SHALL recommend rebalancing
   over scaling.

---

## Risk Classification

- **Inherent risk:** H (scaling modifies infrastructure; impacts capacity)
- **HITL requirement:** Mandatory for all scaling actions

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Detection | Python sidecar | Completion time forecasting |
| Reasoning | Deep (Opus-class) | Scaling recommendation, skew analysis |
| Planning | Deep (Opus-class) | Alternative strategies |
| Memory | Episodic | Prior scaling outcomes |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getLagByPartition()` | kafka-mcp | L | Per-partition lag |
| `getCompletionForecast()` | kafka-mcp | L | ML-based time prediction |
| `getHotPartitionKeys()` | kafka-mcp | L | Skew detection |
| `requestReplicaScale(from, to)` | infra-mcp | H | Scale consumers (gated) |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| LAG-EVAL-01 | Forecast: won't finish before EMEA cutoff | Proposes scale 18→26 |
| LAG-EVAL-02 | All consumers on track | "Completion on schedule" |
| LAG-EVAL-03 | Partition skew detected | Recommends rebalance over scale |
| LAG-EVAL-04 | Scale approved | Calls requestReplicaScale(18, 26) |
| LAG-EVAL-05 | Scale denied | Proposes deferral |
| LAG-EVAL-06 | Hot key identified | Reports key + affected partition |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to Kafka and infra services |
| 6 — Planning | Alternative strategies (scale vs defer) |
| 10 — Goal Setting | Meet cutoff deadline |
| 12 — Human-in-the-Loop | Scaling approval gate |
| 15 — Resource-Aware | Capacity and cost considerations |

---

## Python Sidecar Dependency

- **completion-time-forecaster**: ML model predicting consumer completion
  time based on historical throughput curves, current lag, and partition
  distribution.
