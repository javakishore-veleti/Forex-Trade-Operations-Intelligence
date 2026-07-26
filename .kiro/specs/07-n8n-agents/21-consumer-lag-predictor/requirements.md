# Requirements Document — Consumer-Lag SLA Predictor Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Consumer-Lag SLA Predictor Agent** — a specialized
agent that forecasts whether event-stream consumers will complete processing
before regional cutoff deadlines. When completion is at risk, the agent
proposes scaling actions (replica increase) subject to human approval.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
directly scale infrastructure — it proposes scaling plans via human-gated
`AGENT_TOOL_PROTOCOL` tools.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **ConsumerLagPredictor**: The `AGENT_PLATFORM` workflow forecasting consumer completion.
- **ConsumerLag**: The offset delta between produced and consumed messages per partition.
- **CompletionForecast**: A time estimate (minutes) to fully drain backlog at current throughput.
- **PartitionSkew**: Uneven lag distribution indicating hot-partition problems.
- **ScalingProposal**: A structured recommendation to adjust consumer replicas.

---

## Requirements

### Requirement 1: Lag-Based Completion Forecast

**User Story:** As an operations manager, I want to know whether event
consumers will finish before the regional cutoff, so that I can act
proactively.

#### Acceptance Criteria

1. THE agent SHALL retrieve per-partition lag via `getLagByPartition()`.
2. THE agent SHALL invoke the Python sidecar `getCompletionForecast()` to
   predict time-to-drain based on historical throughput curves.
3. THE agent SHALL compare forecast to the next regional cutoff deadline.
4. THE agent SHALL identify hot partitions via `getHotPartitionKeys()`.
5. WHEN forecast exceeds cutoff, THE agent SHALL flag as SLA_AT_RISK.

---

### Requirement 2: Scaling Plan Proposal

**User Story:** As an infrastructure operator, I want a scaling recommendation
when consumer lag threatens deadlines, so that I can approve and apply it.

#### Acceptance Criteria

1. WHEN SLA_AT_RISK is detected, THE agent SHALL compute required replica
   count using the completion model.
2. THE agent SHALL produce a `ScalingProposal` with: current replicas,
   proposed replicas, estimated completion after scale, confidence.
3. THE agent SHALL present the proposal at a HITL gate for approval.
4. WHEN approved, THE agent SHALL call `requestReplicaScale(from, to)`.

---

### Requirement 3: Partition Skew Analysis

**User Story:** As a Kafka administrator, I want hot-partition identification,
so that I can address key distribution problems.

#### Acceptance Criteria

1. THE agent SHALL identify partitions with lag > 3× median lag.
2. THE agent SHALL call `getHotPartitionKeys()` to retrieve problematic keys.
3. THE agent SHALL explain skew impact on overall completion time.
4. THE agent SHALL recommend rebalance when skew is the primary delay cause.

---

## Risk Classification

- **Inherent risk:** H (scaling proposals affect infrastructure capacity)
- **HITL requirement:** Mandatory for scaling actions

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Detection | Python sidecar (ML model) | Completion-time forecasting |
| Reasoning | Deep (Opus-class) | Scaling plan, skew analysis |
| Planning | Deep (Opus-class) | Alternative strategies |
| Memory | Episodic | Prior lag events and outcomes |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getLagByPartition()` | streaming-mcp | L | Per-partition consumer lag |
| `getCompletionForecast()` | streaming-mcp | L | Python sidecar time-to-drain |
| `getHotPartitionKeys()` | streaming-mcp | L | Hot-partition key distribution |
| `requestReplicaScale(from, to)` | streaming-mcp | H | Scale consumer replicas (gated) |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| LAG-EVAL-01 | Lag exceeds cutoff forecast | SLA_AT_RISK + scaling proposal |
| LAG-EVAL-02 | Scaling approved | Calls requestReplicaScale(18, 26) |
| LAG-EVAL-03 | Scaling denied | Holds; records decision |
| LAG-EVAL-04 | Hot partition detected | Identifies key, explains impact |
| LAG-EVAL-05 | Lag healthy, will complete | "On track for EMEA cutoff" |
| LAG-EVAL-06 | Multiple regions at risk | Prioritizes by cutoff proximity |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to streaming service |
| 6 — Planning | Scaling strategy evaluation |
| 10 — Goal Setting | Completion-before-cutoff goal |
| 12 — Human-in-the-Loop | Scaling approval gate |
| 15 — Resource-Aware | Replica count optimization |

---

## Python Sidecar Dependency

- **completion-time-forecaster**: ML model trained on historical consumer
  throughput curves (per-region, per-topic). Predicts minutes-to-drain given
  current lag, throughput, and time-of-day. Emits forecast envelope.
