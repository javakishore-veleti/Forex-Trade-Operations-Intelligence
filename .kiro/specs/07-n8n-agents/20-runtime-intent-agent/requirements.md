# Requirements Document — Runtime Intent-Inference Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Runtime Intent-Inference Agent** — a specialized
agent that classifies bursts of system activity into recognized operational
intents (e.g., EOD ramp, failover drill, load test, incident) and suppresses
false alarms by correlating behavioral patterns with known business goals.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
take remediation actions — it classifies activity and informs other agents to
suppress noise.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **RuntimeIntentAgent**: The `AGENT_PLATFORM` workflow classifying system activity bursts.
- **ActivityBurst**: A window of elevated metrics, events, or deployments exceeding baseline.
- **OperationalIntent**: A recognized goal label (EOD_RAMP, FAILOVER, LOAD_TEST, INCIDENT, UNKNOWN).
- **BusinessGoal**: A scheduled or known operational purpose aligned with the activity.
- **IntentEnvelope**: The structured response containing intent classification, confidence, and evidence.

---

## Requirements

### Requirement 1: Activity Burst Detection and Classification

**User Story:** As an operations engineer, I want system activity bursts
classified by intent, so that I can distinguish routine operations from
genuine incidents.

#### Acceptance Criteria

1. THE agent SHALL accept a time window and retrieve recent system activity
   via `getRecentActivity(window)`.
2. THE agent SHALL invoke the behavioral clustering sidecar to group
   activity signals by pattern.
3. THE agent SHALL classify clusters into `OperationalIntent` categories:
   EOD_RAMP, FAILOVER, LOAD_TEST, INCIDENT, MAINTENANCE, UNKNOWN.
4. THE agent SHALL provide confidence score (0-1) for the classification.
5. THE agent SHALL correlate activity with known schedules (EOD windows,
   planned maintenance, drill calendars).

---

### Requirement 2: Business Goal Alignment

**User Story:** As a supervisor agent, I want to know if observed activity
aligns with a known business goal, so that I can suppress alert noise
during planned operations.

#### Acceptance Criteria

1. THE agent SHALL call `alignToBusinessGoal()` to check if the activity
   window overlaps with a scheduled business operation.
2. WHEN activity aligns with a known goal, THE agent SHALL mark the intent
   as EXPECTED and include the goal reference.
3. WHEN activity does NOT align, THE agent SHALL mark as UNEXPECTED and
   recommend investigation.
4. THE agent SHALL return suppression guidance for downstream alerting systems.

---

### Requirement 3: False Alarm Suppression Guidance

**User Story:** As a platform operator, I want false alarms suppressed
during recognized operational windows, so that alert fatigue is reduced.

#### Acceptance Criteria

1. THE agent SHALL produce an `IntentEnvelope` with a `suppressAlerts` flag
   when confidence > 0.8 and intent aligns with a known goal.
2. THE suppression guidance SHALL include scope (which services, regions)
   and duration (until expected completion time).
3. THE agent SHALL NOT suppress alerts for INCIDENT or UNKNOWN intents.
4. THE agent SHALL log all suppression recommendations for audit.

---

## Risk Classification

- **Inherent risk:** L (read/classify only — no side effects)
- **HITL requirement:** None (advisory classification)

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Perception | Lightweight (Haiku-class) | Activity signal normalization |
| Detection | Python sidecar | Behavioral clustering |
| Reasoning | Deep (Opus-class) | Intent classification, goal alignment |
| Memory | Episodic | Prior burst patterns and outcomes |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getRecentActivity(window)` | observability-mcp | L | Metrics, events, deploys in time window |
| `classifyIntent()` | observability-mcp | L | Python sidecar behavioral clustering |
| `alignToBusinessGoal()` | calendar-mcp | L | Match activity to scheduled operations |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| INTENT-EVAL-01 | Burst during APAC EOD window | Intent=EOD_RAMP, confidence>0.9, suppress=true |
| INTENT-EVAL-02 | Scaling spike during planned failover | Intent=FAILOVER, aligned=true, suppress=true |
| INTENT-EVAL-03 | Anomalous burst, no known schedule | Intent=UNKNOWN, suppress=false, investigate=true |
| INTENT-EVAL-04 | Load test with matching calendar entry | Intent=LOAD_TEST, aligned=true |
| INTENT-EVAL-05 | Mixed signals: partial EOD + anomaly | Intent=EOD_RAMP, confidence=0.6, suppress=false |
| INTENT-EVAL-06 | Quiet window, no burst | "No significant activity burst detected" |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to observability and calendar services |
| 16 — Reasoning | Intent classification from clustered signals |
| 20 — Exploration | Behavioral pattern discovery |

---

## Python Sidecar Dependency

- **behavioral-cluster-analyzer**: Clusters activity signals (metrics, deploys,
  events) into pattern groups using unsupervised learning. Emits cluster
  envelope with centroid characteristics and temporal profile.
