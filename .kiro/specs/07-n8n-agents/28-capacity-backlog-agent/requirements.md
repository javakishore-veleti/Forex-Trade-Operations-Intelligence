# Requirements Document — Capacity & Backlog Planning Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Capacity & Backlog Planning Agent** — a specialized
agent that forecasts whether processing backlogs will clear before regional
deadlines, and proposes scaling plans (replicas, partitions) subject to
human approval when completion is at risk.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
directly scale infrastructure — it proposes plans via human-gated tools.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **CapacityBacklogAgent**: The `AGENT_PLATFORM` workflow planning capacity vs backlog.
- **Backlog**: The count of unprocessed items pending completion per region/service.
- **CompletionEstimate**: Predicted time-to-clear based on current throughput.
- **ScalingPlan**: A structured proposal for infrastructure changes to meet deadline.
- **DeadlineRisk**: A condition where estimated completion exceeds next cutoff.

---

## Requirements

### Requirement 1: Backlog vs Deadline Assessment

**User Story:** As an operations manager, I want to know if current backlogs
will clear before deadlines, so that I can intervene proactively.

#### Acceptance Criteria

1. THE agent SHALL call `getBacklog(region)` to retrieve current processing
   backlog per region.
2. THE agent SHALL call `getCompletionEstimate()` to predict time-to-clear
   at current throughput.
3. THE agent SHALL compare estimate against the next regional deadline.
4. THE agent SHALL factor in: per-currency complexity, retry volume,
   downstream capacity, and historical curves.
5. WHEN estimate exceeds deadline, THE agent SHALL flag as DEADLINE_AT_RISK.

---

### Requirement 2: Scaling Plan Proposal

**User Story:** As an infrastructure operator, I want a scaling plan proposed
when backlog threatens deadlines, so that I can approve targeted capacity
increases.

#### Acceptance Criteria

1. WHEN DEADLINE_AT_RISK is detected, THE agent SHALL call
   `proposeScalingPlan()` to compute required resources.
2. THE scaling plan SHALL include: current replicas, proposed replicas,
   current estimate, post-scale estimate, confidence.
3. THE plan SHALL consider alternatives: scaling vs deferral vs
   prioritization.
4. THE plan SHALL be presented at a HITL gate for approval.
5. WHEN approved, THE agent SHALL call `applyScalingPlan()`.

---

### Requirement 3: Multi-Factor Capacity Analysis

**User Story:** As a capacity planner, I want multi-factor analysis of
processing bottlenecks, so that I understand root causes beyond raw numbers.

#### Acceptance Criteria

1. THE agent SHALL identify: CPU-bound, I/O-bound, downstream-limited, or
   partition-skewed bottleneck types.
2. THE agent SHALL report concurrent constraints (DB load, Kafka throughput,
   downstream service limits).
3. THE agent SHALL recommend the most effective scaling lever.

---

## Risk Classification

- **Inherent risk:** H (scaling proposals affect infrastructure capacity and cost)
- **HITL requirement:** Mandatory for scaling plan application

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Detection | Python sidecar | Capacity/completion model |
| Reasoning | Deep (Opus-class) | Bottleneck analysis, alternative strategies |
| Planning | Deep (Opus-class) | Scaling plan design |
| Execution | Deep (Opus-class) | Plan orchestration |
| Memory | Episodic | Prior scaling events and outcomes |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getBacklog(region)` | processing-mcp | L | Current backlog per region |
| `getCompletionEstimate()` | processing-mcp | L | Python sidecar time estimate |
| `proposeScalingPlan()` | processing-mcp | L | Compute required resources |
| `applyScalingPlan()` | processing-mcp | H | Apply scaling (gated) |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| CAP-EVAL-01 | 2.4M backlog, 47min est vs 31min deadline | Scaling plan: +8 replicas |
| CAP-EVAL-02 | Scaling approved | Calls applyScalingPlan |
| CAP-EVAL-03 | Scaling denied | Suggests alternative (prioritize, defer) |
| CAP-EVAL-04 | Downstream bottleneck | Reports DB as limiting factor |
| CAP-EVAL-05 | On track | "Backlog will clear 9 min before deadline" |
| CAP-EVAL-06 | Multiple regions at risk | Prioritized by deadline proximity |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to processing and capacity services |
| 6 — Planning | Scaling strategy evaluation |
| 12 — Human-in-the-Loop | Scaling approval gate |
| 15 — Resource-Aware | Capacity optimization |
| 19 — Prioritization | Deadline-based prioritization |

---

## Python Sidecar Dependency

- **capacity-forecast-model**: ML model trained on historical processing
  throughput (per-region, per-service, per-time-of-day). Predicts time-to-clear
  and required capacity for deadline achievement.
