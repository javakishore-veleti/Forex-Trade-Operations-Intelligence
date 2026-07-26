# Requirements Document — Shadow Rule Simulator Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Shadow Rule Simulator Agent** — a specialized agent
that converts natural-language rule requests into `RULES_ENGINE` rule language
(DRL), deploys them to a sandboxed shadow pod, replays historical events against
the shadow rules, computes an impact diff vs production, and presents the
results for human-approved production deployment.

This is the SAFE alternative to autonomous threshold adjustment. The agent never
touches production rules without explicit human approval.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **ShadowRuleSimulator**: The `AGENT_PLATFORM` workflow for NL→DRL→shadow→diff→deploy.
- **DRL**: Drools Rule Language — the `RULES_ENGINE` rule definition format.
- **ShadowPod**: An isolated `SERVICE_FRAMEWORK` instance running in simulation mode with no production side effects.
- **HistoricalReplay**: Re-processing a window of past events through the shadow rule set.
- **ImpactDiff**: The comparison between shadow rule outcomes and production rule outcomes.
- **DeployGate**: The HITL checkpoint before promoting a shadow rule to production.

---

## Requirements

### Requirement 1: Natural Language to DRL Conversion

**User Story:** As a rules analyst, I want to describe a new rule in plain
English and have the agent generate valid DRL, so that I can prototype rules
without writing code.

#### Acceptance Criteria

1. THE agent SHALL accept a natural-language rule description (e.g., "reject
   EUR/TRY trades above 5M notional when volatility exceeds 3σ").
2. THE agent SHALL use the `ReasoningModel` (Opus-class) with DRL corpus
   embeddings to generate syntactically valid DRL.
3. THE generated DRL SHALL be validated by the shadow `RULES_ENGINE` pod
   (parse + compile check) before proceeding.
4. WHEN the DRL fails validation, THE agent SHALL use a reflection loop
   (max 3 iterations) to correct the rule based on error messages.
5. THE agent SHALL present the generated DRL to the analyst for review
   before shadow deployment.

---

### Requirement 2: Shadow Deployment and Historical Replay

**User Story:** As a rules manager, I want to replay historical events against
the proposed rule in a sandbox, so that I can see impact without risk.

#### Acceptance Criteria

1. THE agent SHALL call `loadShadowRule(drl)` to deploy the validated rule
   to the shadow pod.
2. THE agent SHALL call `replayHistoricalEvents(window)` to process a
   configurable time window of past events (default: 5 business days).
3. THE replay SHALL use actual historical trade events from the
   `EVENT_STREAM` archive.
4. THE shadow pod SHALL have no connection to production state stores.

---

### Requirement 3: Impact Diff and Analysis

**User Story:** As a risk stakeholder, I want to see exactly how the proposed
rule would have changed outcomes compared to production, so that I can make
an informed deployment decision.

#### Acceptance Criteria

1. THE agent SHALL call `readShadowRiskResults()` to collect shadow outcomes.
2. THE agent SHALL call `diffAgainstProduction()` to compute the delta.
3. THE diff SHALL include: trades newly rejected, trades newly accepted,
   risk score changes, limit breach differences.
4. THE agent SHALL use the `ReasoningModel` to explain the diff in business
   terms (who is affected, magnitude, edge cases).

---

### Requirement 4: Human-Gated Production Deployment

**User Story:** As a rules governance owner, I want production deployment of
any new rule to require my explicit approval after reviewing the impact diff.

#### Acceptance Criteria

1. THE agent SHALL present the impact diff at the HITL gate before any
   production deployment.
2. THE agent SHALL NOT deploy to production without explicit human approval.
3. WHEN approved, THE agent SHALL hand off to the standard rule deployment
   pipeline (out of scope for this agent — it produces the artifact).
4. WHEN denied, THE agent SHALL archive the shadow results and DRL for future
   reference.

---

## Risk Classification

- **Inherent risk:** H (shadow-only execution, but gated production deploy)
- **HITL requirement:** Mandatory — production deploy gated

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Reasoning/Codegen | Deep (Opus-class) | NL→DRL generation, impact explanation |
| Embedding | Embedding model | DRL corpus similarity for generation quality |
| Reflection | Deep (Opus-class) | Self-correction loop for DRL validation errors |
| Detection | Deterministic (shadow RULES_ENGINE) | Replay + diff computation |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `loadShadowRule(drl)` | shadow-rules-mcp | M | Deploy DRL to sandbox |
| `replayHistoricalEvents(window)` | shadow-rules-mcp | L | Replay events in sandbox |
| `readShadowRiskResults()` | shadow-rules-mcp | L | Collect shadow outcomes |
| `diffAgainstProduction()` | shadow-rules-mcp | L | Compare shadow vs prod |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| SHAD-EVAL-01 | "Reject EUR/TRY trades above 5M when vol > 3σ" | Generates DRL, validates, replays, shows diff |
| SHAD-EVAL-02 | Invalid NL description | Asks for clarification |
| SHAD-EVAL-03 | Generated DRL fails validation | Reflection loop corrects (max 3 attempts) |
| SHAD-EVAL-04 | Shadow diff shows 0 impact | Reports "no trades affected in window" |
| SHAD-EVAL-05 | Deploy approved | Hands off DRL artifact to deployment pipeline |
| SHAD-EVAL-06 | Deploy denied | Archives results, logs denial |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 4 — Reflection | DRL validation self-correction loop |
| 5 — Tool Use | Shadow pod MCP tools |
| 6 — Planning | Multi-step: generate → validate → deploy → replay → diff |
| 12 — Human-in-the-Loop | Production deploy gate |
| 17 — Evaluation | Impact diff as eval metric |
| 18 — Guardrails | Sandboxed execution, no production side effects |

---

## Python Sidecar Dependency

- **NL→DRL prompt glue**: Python sidecar provides DRL corpus embeddings for
  retrieval-augmented generation. The DRL parse/validate/simulate is done by
  the shadow `SERVICE_FRAMEWORK` pod (Java/Drools), NOT Python.
