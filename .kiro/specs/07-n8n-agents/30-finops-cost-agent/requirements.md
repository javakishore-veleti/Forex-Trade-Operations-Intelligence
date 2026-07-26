# Requirements Document — FinOps Cost-Anomaly Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **FinOps Cost-Anomaly Agent** — a specialized agent
that detects cost anomalies, correlates spikes to deployments or volume
changes, and proposes rightsizing actions subject to human approval.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
directly scale down infrastructure — it proposes cost reduction actions via
human-gated tools.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **FinOpsCostAgent**: The `AGENT_PLATFORM` workflow detecting cost anomalies.
- **CostAnomaly**: An unexpected spike or pattern change in infrastructure costs.
- **CostCorrelation**: The linkage between a cost change and a deploy/volume event.
- **RightsizingProposal**: A recommendation to adjust resource allocation for cost savings.
- **IdleCapacity**: Provisioned but unused resources during low-volume periods.

---

## Requirements

### Requirement 1: Cost Anomaly Detection

**User Story:** As a FinOps lead, I want cost spikes detected and explained,
so that I can understand root causes quickly.

#### Acceptance Criteria

1. THE agent SHALL call `getCostByService()` to retrieve per-service cost
   breakdowns.
2. THE agent SHALL detect anomalies exceeding baseline by configurable
   threshold (default: 30% deviation).
3. THE agent SHALL identify the specific resource category causing the spike
   (compute, storage, network, data transfer).
4. THE agent SHALL flag cost spikes during low-volume periods as
   INEFFICIENCY candidates.

---

### Requirement 2: Deploy-to-Cost Correlation

**User Story:** As a platform engineer, I want cost spikes correlated to
recent deployments, so that I can identify the change that caused them.

#### Acceptance Criteria

1. THE agent SHALL call `correlateCostToDeploy()` to match cost changes
   with deployment events within a configurable window.
2. THE agent SHALL explain the causal mechanism (e.g., "rule change doubled
   risk-engine invocations").
3. THE agent SHALL identify FX-volume-related cost changes (expected load
   variations) vs deployment-driven changes (unexpected).

---

### Requirement 3: Rightsizing Proposal

**User Story:** As an infrastructure manager, I want rightsizing proposals
with my approval required before any scale-down, so that cost savings don't
impact SLAs.

#### Acceptance Criteria

1. THE agent SHALL call `getIdleCapacity()` to identify underutilized
   resources.
2. THE agent SHALL call `proposeRightsizing()` to generate optimization
   recommendations.
3. THE proposal SHALL include: current allocation, proposed allocation,
   estimated savings, risk assessment.
4. THE proposal SHALL be presented at a HITL gate.
5. WHEN approved, THE agent SHALL call `applyScaleDown()`.

---

## Risk Classification

- **Inherent risk:** H (scale-down affects capacity and potentially SLAs)
- **HITL requirement:** Mandatory for rightsizing/scale-down actions

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Detection | Python sidecar | Cost time-series anomaly |
| Reasoning | Deep (Opus-class) | Cost-to-cause correlation |
| Planning | Deep (Opus-class) | Rightsizing strategy |
| Execution | Deep (Opus-class) | Action orchestration |
| Memory | Episodic | Prior cost events and actions |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getCostByService()` | finops-mcp | L | Per-service cost breakdown |
| `correlateCostToDeploy()` | finops-mcp | L | Deploy-to-cost linkage |
| `getIdleCapacity()` | finops-mcp | L | Underutilized resources |
| `proposeRightsizing()` | finops-mcp | L | Optimization recommendations |
| `applyScaleDown()` | finops-mcp | H | Scale down resources (gated) |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| FINOPS-EVAL-01 | Risk-engine cost +45% | Correlated to rule 7.14 deploy |
| FINOPS-EVAL-02 | Scale-down approved | Calls applyScaleDown |
| FINOPS-EVAL-03 | Weekend idle capacity | Rightsizing proposed |
| FINOPS-EVAL-04 | Scale-down denied | Logs decision, monitors |
| FINOPS-EVAL-05 | Volume-driven cost increase | Flagged as EXPECTED |
| FINOPS-EVAL-06 | Stable costs | "No anomalies detected" |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to FinOps service |
| 10 — Goal Setting | Cost optimization within SLA bounds |
| 12 — Human-in-the-Loop | Scale-down approval gate |
| 15 — Resource-Aware | Capacity-vs-cost optimization |

---

## Python Sidecar Dependency

- **cost-anomaly-detector**: Time-series anomaly detection on cloud billing
  data (per-service, per-resource-type). Emits anomaly envelope with
  deviation magnitude, trend, and temporal context.
