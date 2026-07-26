# Requirements Document — Retry-Storm & Backpressure Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Retry-Storm & Backpressure Agent** — a specialized
agent that detects retry amplification cascades and circuit-breaker storms,
identifies the root service, and proposes targeted backpressure actions with
human approval.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
automatically apply backpressure — it proposes actions via human-gated tools.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **RetryStormAgent**: The `AGENT_PLATFORM` workflow detecting retry amplification.
- **RetryAmplification**: A condition where retries create cascading load exceeding normal traffic.
- **BreakerCascade**: Multiple circuit breakers opening in sequence due to downstream propagation.
- **BackpressureAction**: A gated action to rate-limit or trip breakers at specific points.
- **CascadePath**: The graph traversal showing propagation from root to affected services.

---

## Requirements

### Requirement 1: Retry Amplification Detection

**User Story:** As a reliability engineer, I want retry storms detected before
they cascade, so that I can apply targeted backpressure.

#### Acceptance Criteria

1. THE agent SHALL call `getRetryAmplification()` to detect retry-rate
   growth exceeding baseline.
2. THE agent SHALL call `getOpenBreakers()` to identify open circuit breakers.
3. THE agent SHALL identify the propagation path from root cause to symptoms.
4. THE agent SHALL distinguish root-cause service from symptomatic services.

---

### Requirement 2: Cascade Path Analysis

**User Story:** As a platform engineer, I want to understand the cascade path,
so that I can apply backpressure at the right point.

#### Acceptance Criteria

1. THE agent SHALL call `getCascadePath(service)` to trace retry
   propagation through the dependency graph.
2. THE agent SHALL identify the single root service where backpressure is
   most effective.
3. THE agent SHALL report: retry rate per service, breaker state, and
   estimated time-to-recovery.

---

### Requirement 3: Backpressure Application

**User Story:** As an operations lead, I want targeted backpressure proposed
with my approval, so that I can shed load at the correct point without
disrupting healthy flows.

#### Acceptance Criteria

1. WHEN retry storm is detected, THE agent SHALL propose backpressure
   action at a HITL gate with: target service, type (rate-limit vs
   breaker-trip), affected flows, recovery estimate.
2. WHEN approved, THE agent SHALL call `applyBackpressure()` or
   `tripBreaker()`.
3. THE agent SHALL NOT apply backpressure to healthy services.
4. THE agent SHALL prefer backpressure at root over downstream symptoms.

---

## Risk Classification

- **Inherent risk:** H (backpressure affects transaction processing capacity)
- **HITL requirement:** Mandatory for backpressure/breaker actions

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Detection | Python sidecar | Cascade graph analysis |
| Reasoning | Deep (Opus-class) | Root vs symptom identification |
| Planning | Deep (Opus-class) | Backpressure strategy |
| Memory | Episodic | Prior storms and resolutions |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getRetryAmplification()` | observability-mcp | L | Retry rate growth metrics |
| `getOpenBreakers()` | observability-mcp | L | Circuit breaker states |
| `getCascadePath(service)` | graph-mcp | L | Dependency propagation trace |
| `applyBackpressure()` | routing-mcp | H | Rate-limit target service (gated) |
| `tripBreaker()` | routing-mcp | H | Trip breaker at target (gated) |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| RETRY-EVAL-01 | 10× retry amplification on enrichment | Root identified, backpressure proposed |
| RETRY-EVAL-02 | Backpressure approved | Calls applyBackpressure |
| RETRY-EVAL-03 | Multiple breakers open | Cascade path traced to root |
| RETRY-EVAL-04 | False alarm (brief spike) | "Retry rate stabilizing, no action" |
| RETRY-EVAL-05 | Backpressure denied | Logs decision, monitors |
| RETRY-EVAL-06 | Multi-root cascade | Both roots identified, prioritized |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to observability and routing services |
| 11 — Exception Handling | Storm-specific recovery |
| 12 — Human-in-the-Loop | Backpressure approval gate |
| 15 — Resource-Aware | Load-shedding optimization |

---

## Python Sidecar Dependency

- **cascade-graph-analyzer**: Analyzes retry patterns across the service
  dependency graph. Identifies amplification factor, propagation delay,
  and optimal intervention point.
