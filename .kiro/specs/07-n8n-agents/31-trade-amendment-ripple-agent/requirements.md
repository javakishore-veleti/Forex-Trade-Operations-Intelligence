# Requirements Document — Trade Amendment Ripple Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Trade Amendment Ripple Agent** — a specialized
agent that tracks downstream effects when a trade is amended or cancelled,
verifying that all dependent systems (risk, settlement, reporting) have
correctly propagated the change.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
amend trades — it verifies that amendments have rippled correctly and
proposes corrective actions via human-gated tools when gaps are found.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **TradeAmendmentRippleAgent**: The `AGENT_PLATFORM` workflow tracking amendment effects.
- **RippleEffect**: A downstream consequence of a trade amendment or cancellation.
- **DownstreamGap**: A dependent system that did NOT react to the amendment.
- **CorrectionAction**: A gated action to trigger missing downstream recalculations.
- **RippleMap**: The graph of expected vs actual downstream reactions.

---

## Requirements

### Requirement 1: Downstream Effect Tracking

**User Story:** As a trade operations analyst, I want to verify all downstream
systems processed a trade amendment, so that no stale data remains.

#### Acceptance Criteria

1. THE agent SHALL be triggered by `TradeAmended` or `TradeCancelled` events.
2. THE agent SHALL call `findDownstreamEffects(tradeId)` to identify expected
   downstream reactions via graph traversal.
3. THE agent SHALL verify: risk recalculation triggered, settlement
   instruction updated, report amended.
4. THE agent SHALL call `checkRiskRecalcTriggered()`,
   `checkSettlementWithdrawn()`, and `checkReportAmended()` for each
   expected effect.

---

### Requirement 2: Gap Detection and Explanation

**User Story:** As a risk manager, I want missing downstream reactions
identified with explanation, so that I understand the scope of stale data.

#### Acceptance Criteria

1. THE agent SHALL identify which downstream steps did NOT fire.
2. THE agent SHALL explain the likely cause (processing lag vs missing
   event vs system failure).
3. THE agent SHALL report the business impact of each gap (stale risk,
   incorrect settlement, missing report).
4. THE agent SHALL produce a `RippleMap` showing expected vs actual states.

---

### Requirement 3: Corrective Action Proposal

**User Story:** As an operations supervisor, I want missing recalculations
triggered with my approval, so that stale data is corrected.

#### Acceptance Criteria

1. WHEN downstream gaps are detected, THE agent SHALL propose corrective
   actions at a HITL gate.
2. THE proposal SHALL include: affected system, gap type, proposed fix
   (recalculation, withdrawal, re-submission).
3. WHEN approved, THE agent SHALL call `requestMissingRecalc()`.
4. THE agent SHALL NOT auto-trigger recalculations without approval.

---

## Risk Classification

- **Inherent risk:** M (triggers recalculations and report amendments)
- **HITL requirement:** Required for corrective actions

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Perception | Lightweight (Haiku-class) | Event parsing |
| Reasoning | Deep (Opus-class) | Gap explanation, impact assessment |
| Planning | Deep (Opus-class) | Corrective sequence design |
| Policy | Deterministic | Expected downstream effects |
| Memory | Episodic | Prior amendment ripple patterns |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `findDownstreamEffects(tradeId)` | graph-mcp | L | Expected downstream reactions |
| `checkRiskRecalcTriggered()` | risk-calculation-mcp | L | Verify risk reacted |
| `checkSettlementWithdrawn()` | settlement-mcp | L | Verify settlement updated |
| `checkReportAmended()` | reporting-mcp | L | Verify report updated |
| `requestMissingRecalc()` | risk-calculation-mcp | M | Trigger recalculation (gated) |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| RIPPLE-EVAL-01 | Trade FX-000042 amended | All downstream verified |
| RIPPLE-EVAL-02 | Risk recalc missing | Gap detected, correction proposed |
| RIPPLE-EVAL-03 | Settlement not withdrawn | Gap + business impact reported |
| RIPPLE-EVAL-04 | Correction approved | Calls requestMissingRecalc |
| RIPPLE-EVAL-05 | Trade cancelled, all clean | "All downstream effects confirmed" |
| RIPPLE-EVAL-06 | Report not amended | Reporting gap flagged |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to graph, risk, settlement, reporting |
| 6 — Planning | Corrective sequence design |
| 12 — Human-in-the-Loop | Correction action gate |
| 14 — Inter-Agent | Coordinates with lifecycle agent |

---

## Python Sidecar Dependency

- None — downstream effect verification is deterministic (state check).
  Graph traversal uses Cypher templates.
