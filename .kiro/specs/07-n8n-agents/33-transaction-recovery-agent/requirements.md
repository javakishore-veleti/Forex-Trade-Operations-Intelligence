# Requirements Document — Transaction Recovery Coordinator Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Transaction Recovery Coordinator Agent** — a
specialized multi-agent workflow that investigates stuck transactions,
plans recovery actions, validates safety, executes step-by-step recovery,
and produces an audit trail.

This agent is implemented as an `AGENT_PLATFORM` workflow export. Every
recovery action is narrow, idempotent, dry-run capable, and human-gated.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **TransactionRecoveryAgent**: The `AGENT_PLATFORM` workflow coordinating recovery.
- **RecoveryPlan**: An ordered sequence of narrow, idempotent recovery steps.
- **SafetyCheck**: A pre-execution verification that no settlement or downstream effect conflicts.
- **RecoveryStep**: A single atomic operation (cache invalidation, event replay, state correction).
- **RecoveryCase**: The end-to-end lifecycle from investigation to closure.

---

## Requirements

### Requirement 1: Investigation Phase

**User Story:** As an operations analyst, I want stuck transactions fully
investigated before any recovery attempt, so that the recovery plan is
informed by complete state.

#### Acceptance Criteria

1. THE agent SHALL call `verifyNoSettlement()` to confirm no money has moved.
2. THE agent SHALL call `checkReplayKey()` to verify replay is idempotent.
3. THE agent SHALL gather state from all systems (Postgres, MongoDB, Redis,
   Kafka) via typed tools.
4. THE agent SHALL produce an investigation report with: current state per
   system, expected state, divergence map, and candidate actions.

---

### Requirement 2: Recovery Planning

**User Story:** As a recovery coordinator, I want an ordered recovery plan
with each step validated, so that execution is predictable and safe.

#### Acceptance Criteria

1. THE agent SHALL produce a `RecoveryPlan` with ordered steps.
2. EACH step SHALL be: narrow (one action), idempotent (safe to retry),
   reversible or verifiable.
3. THE plan SHALL include pre-conditions and expected post-conditions
   for each step.
4. THE agent SHALL identify risks and abort conditions for each step.

---

### Requirement 3: Safety Verification

**User Story:** As a risk stakeholder, I want each recovery step verified
for safety before execution, so that recovery doesn't create new problems.

#### Acceptance Criteria

1. BEFORE each step, THE agent SHALL verify pre-conditions are met.
2. THE agent SHALL verify no parallel processes conflict with recovery.
3. THE agent SHALL call `compareState()` after each step to confirm
   expected outcome.
4. IF any step fails verification, THE agent SHALL halt and escalate.

---

### Requirement 4: Human-Gated Step-by-Step Execution

**User Story:** As an operations lead, I want recovery executed step by
step with my approval at each critical point.

#### Acceptance Criteria

1. THE full recovery plan SHALL be presented at a HITL gate before
   execution begins.
2. HIGH-risk steps (event replay, state correction) SHALL have individual
   HITL gates.
3. WHEN approved, THE agent SHALL call the appropriate MCP tools:
   `invalidateCache()`, `replayEvent()`, `compareState()`.
4. THE agent SHALL produce an audit record for each executed step.
5. ON completion, THE agent SHALL call `closeRecoveryCase()`.

---

## Risk Classification

- **Inherent risk:** H (recovery modifies state, replays events)
- **HITL requirement:** Mandatory for plan approval and step execution

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Investigation | Deep (Opus-class) | Multi-system state analysis |
| Planning | Deep (Opus-class) | Recovery plan design |
| Safety | Deep (Opus-class) | Pre/post condition verification |
| Execution | Deep (Opus-class) | Step orchestration |
| Memory | Episodic | Prior recovery cases |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `verifyNoSettlement()` | settlement-mcp | L | Confirm no money moved |
| `checkReplayKey()` | trade-lifecycle-mcp | L | Verify replay idempotency |
| `invalidateCache()` | trade-lifecycle-mcp | M | Clear stale cache (gated) |
| `replayEvent()` | trade-lifecycle-mcp | H | Replay trade event (gated) |
| `compareState()` | state-reconciliation-mcp | L | Post-step verification |
| `closeRecoveryCase()` | trade-lifecycle-mcp | L | Close case with audit |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| RECOV-EVAL-01 | Stuck trade FX-000042 | Investigation → plan → execute |
| RECOV-EVAL-02 | Plan approved | Steps execute in order |
| RECOV-EVAL-03 | Step fails verification | Halts, escalates |
| RECOV-EVAL-04 | Settlement already occurred | Recovery blocked; cannot proceed |
| RECOV-EVAL-05 | Cache stale only | Simple invalidation + replay |
| RECOV-EVAL-06 | Multi-system divergence | Complex plan with multiple steps |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to lifecycle, settlement, reconciliation |
| 6 — Planning | Recovery plan design |
| 7 — Multi-Agent | Investigation→Planning→Safety→Execution→Audit |
| 11 — Exception Handling | Stuck-trade recovery |
| 12 — Human-in-the-Loop | Plan + step approval gates |
| 14 — Inter-Agent | Coordinates with other agents for investigation |

---

## Python Sidecar Dependency

- None — recovery is deterministic. All state checks and actions are via
  typed MCP tools to Spring Boot services.
