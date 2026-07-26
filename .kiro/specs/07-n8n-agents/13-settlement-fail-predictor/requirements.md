# Requirements Document — Settlement-Fail Prediction Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Settlement-Fail Prediction Agent** — a specialized
agent that identifies trades at risk of settlement failure before the
settlement window. It combines ML-based probability scoring (Python sidecar)
with deterministic checks (missing SSI, nostro shortfalls) and escalates
high-risk settlements for human intervention.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
execute settlements — it predicts failures and escalates via human-gated
actions.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **SettlementFailPredictor**: The `AGENT_PLATFORM` workflow predicting settlement failures.
- **SSI**: Standing Settlement Instructions — the payment routing for a counterparty.
- **NostroShortfall**: Insufficient balance in the nostro account for the settlement currency.
- **FailProbability**: ML-computed probability of settlement failure (0-1).
- **EscalationPackage**: Structured report of at-risk settlements for operations team.

---

## Requirements

### Requirement 1: Pre-Settlement Risk Sweep

**User Story:** As a settlements manager, I want trades approaching settlement
screened for failure risk, so that I can intervene before the deadline.

#### Acceptance Criteria

1. THE agent SHALL be triggered during the pre-settlement window (configurable,
   default: T-1 business day).
2. THE agent SHALL retrieve trades approaching settlement via the settlement
   service.
3. FOR EACH trade, THE agent SHALL call `getMissingSSI(tradeId)` to check
   for incomplete settlement instructions.
4. FOR EACH trade, THE agent SHALL call `getNostroShortfall(ccy)` to check
   for funding gaps.
5. THE agent SHALL call `predictFailProbability()` (Python ML sidecar) for
   each at-risk trade.

---

### Requirement 2: Prioritized Risk Ranking

**User Story:** As a settlements operator, I want at-risk trades ranked by
fail probability and impact, so that I address the highest-risk items first.

#### Acceptance Criteria

1. THE agent SHALL rank at-risk trades by: fail probability, settlement
   amount, and deadline proximity.
2. THE agent SHALL group failures by reason type: missing SSI, nostro
   shortfall, counterparty history, multiple factors.
3. THE agent SHALL use the `ReasoningModel` to explain why each trade is
   at risk.
4. THE agent SHALL identify clusters (e.g., "all JPY trades for counterparty
   FX-CP-003 lack SSI").

---

### Requirement 3: Escalation with HITL Gate

**User Story:** As a risk stakeholder, I want high-probability failures
escalated for manual intervention, with approval required before any
remediation action.

#### Acceptance Criteria

1. WHEN fail probability exceeds threshold (configurable, default > 0.7),
   THE agent SHALL produce an `EscalationPackage`.
2. THE escalation SHALL be presented at a HITL gate with: trade details,
   failure reason, probability, remediation options.
3. WHEN escalation is approved, THE agent SHALL call
   `escalateSettlementRisk()` to notify operations.
4. THE agent SHALL NOT auto-remediate settlement issues.

---

## Risk Classification

- **Inherent risk:** H (escalation triggers operational intervention for real settlements)
- **HITL requirement:** Mandatory for escalation actions

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Detection | Python sidecar (ML model) | Fail probability prediction |
| Reasoning | Deep (Opus-class) | Explanation, cluster identification |
| Policy | Deterministic | SSI/nostro checks |
| Memory | Episodic | Prior fail patterns |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getMissingSSI(tradeId)` | settlement-mcp | L | Check SSI completeness |
| `getNostroShortfall(ccy)` | settlement-mcp | L | Check nostro funding |
| `predictFailProbability()` | settlement-mcp | L | ML fail probability |
| `escalateSettlementRisk()` | settlement-mcp | H | Escalate to operations (gated) |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| SETT-EVAL-01 | Pre-settlement sweep, 3 at-risk | Ranked list with explanations |
| SETT-EVAL-02 | Trade missing SSI | Identifies, explains, escalates |
| SETT-EVAL-03 | JPY nostro shortfall | Groups affected JPY trades |
| SETT-EVAL-04 | Escalation approved | Calls escalateSettlementRisk |
| SETT-EVAL-05 | All trades healthy | "No at-risk settlements" |
| SETT-EVAL-06 | Low probability (<0.3) | Reports but does not escalate |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to settlement service |
| 10 — Goal Setting | Settlement success goal |
| 12 — Human-in-the-Loop | Escalation gate |
| 19 — Prioritization | Risk-ranked at-risk list |

---

## Python Sidecar Dependency

- **settlement-fail-predictor**: ML model trained on historical settlement
  outcomes, predicts fail probability based on SSI status, nostro balance,
  counterparty history, and temporal features.
