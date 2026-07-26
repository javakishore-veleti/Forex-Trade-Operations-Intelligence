# Requirements Document — Duplicate Business-Effect Guard Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Duplicate Business-Effect Guard Agent** — a
specialized agent that detects double-booking or duplicate settlement
effects (e.g., replay causing real financial duplication), then proposes
dry-run reversal with human approval.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
automatically reverse — it detects duplicate financial effects and proposes
reversal via human-gated, dry-run-first tools.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **DuplicateEffectGuard**: The `AGENT_PLATFORM` workflow detecting double-effects.
- **DoubleBooking**: Two distinct positions/settlements created for what should be one.
- **IdempotencyViolation**: Two events sharing an idempotency key with divergent payloads.
- **DryRunReversal**: A simulated reversal showing what would change before actual execution.
- **EffectDiff**: The comparison of two effect records that should be identical.

---

## Requirements

### Requirement 1: Double-Booking Detection

**User Story:** As a settlements manager, I want double-bookings detected
immediately, so that duplicate financial exposure is identified before
settlement.

#### Acceptance Criteria

1. THE agent SHALL be triggered when: a replay is executed, or an
   idempotency-key collision with divergent payloads is detected.
2. THE agent SHALL call `checkIdempotencyConsumed(key)` to verify
   idempotency store state.
3. THE agent SHALL call `findDoubleBooking(tradeId)` to detect duplicate
   position entries.
4. THE agent SHALL call `findDuplicateSettlementInstruction()` to detect
   duplicated settlement instructions.

---

### Requirement 2: Real Effect vs Benign Retry Classification

**User Story:** As a risk analyst, I want duplicates classified as real
financial double-effect vs benign retry (no new effect), so that I only
act on genuine duplicates.

#### Acceptance Criteria

1. THE agent SHALL use effect-diff logic to compare duplicate records.
2. THE agent SHALL classify: REAL_DOUBLE_EFFECT (both created positions/
   settlements) vs BENIGN_RETRY (idempotent, no new effect).
3. THE agent SHALL explain the classification rationale.
4. THE agent SHALL report the financial exposure of real duplicates.

---

### Requirement 3: Dry-Run Reversal Proposal

**User Story:** As an operations lead, I want a dry-run reversal showing
the impact before any actual reversal, with my approval required.

#### Acceptance Criteria

1. WHEN a REAL_DOUBLE_EFFECT is detected, THE agent SHALL call
   `reverseDuplicateEffect()` in dry-run mode first.
2. THE dry-run SHALL show: what will be reversed, affected systems,
   and downstream consequences.
3. THE reversal proposal SHALL be presented at a HITL gate.
4. WHEN approved, THE agent SHALL execute the actual reversal.
5. THE agent SHALL NEVER auto-reverse without human approval.

---

## Risk Classification

- **Inherent risk:** H (reversal moves money/positions)
- **HITL requirement:** Mandatory for all reversal actions

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Detection | Deterministic | Effect-diff comparison |
| Reasoning | Deep (Opus-class) | Real vs benign classification |
| Policy | Deterministic | Idempotency rules |
| Memory | Episodic | Prior duplicate events |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `checkIdempotencyConsumed(key)` | trade-lifecycle-mcp | L | Idempotency store state |
| `findDoubleBooking(tradeId)` | trade-lifecycle-mcp | L | Duplicate position check |
| `findDuplicateSettlementInstruction()` | settlement-mcp | L | Duplicate SI check |
| `reverseDuplicateEffect()` | trade-lifecycle-mcp | H | Dry-run then actual reversal (gated) |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| DUP-EVAL-01 | Replay creates double settlement | REAL_DOUBLE detected |
| DUP-EVAL-02 | Dry-run reversal shown | Impact displayed to approver |
| DUP-EVAL-03 | Reversal approved | Executes reverseDuplicateEffect |
| DUP-EVAL-04 | Benign retry (idempotent) | BENIGN, no action |
| DUP-EVAL-05 | Key collision, different payload | Flagged as conflict |
| DUP-EVAL-06 | Reversal denied | Logs, escalates to manager |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to lifecycle and settlement services |
| 11 — Exception Handling | Double-effect recovery |
| 12 — Human-in-the-Loop | Reversal approval gate |
| 18 — Guardrails | Dry-run before actual execution |

---

## Python Sidecar Dependency

- None — all detection is deterministic (effect-diff, idempotency store
  checks). No ML required.
