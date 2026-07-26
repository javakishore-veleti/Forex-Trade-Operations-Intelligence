# Requirements Document — Trade Risk Explainability Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Trade Risk Explainability Agent** — a specialized
agent that provides multi-factor explanations for trade risk changes. When a
risk score increases, new limits are breached, or a risk recalculation produces
unexpected results, this agent traces the contributing factors (market data,
rule changes, trade characteristics, counterparty shifts) and produces a
human-readable explanation with full provenance.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
compute risk — it explains risk results from deterministic `SERVICE_FRAMEWORK`
services via typed `AGENT_TOOL_PROTOCOL` tools.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **RiskExplainabilityAgent**: The `AGENT_PLATFORM` workflow providing multi-factor risk explanations.
- **RiskResult**: The deterministic output from the `RULES_ENGINE` and risk calculation service containing scores, contributing factors, and rules fired.
- **RuleTrace**: The sequence of `RULES_ENGINE` rules that fired for a given risk calculation, including input/output values.
- **MarketSnapshot**: Point-in-time market data (rates, volatility) from the market data service.
- **ContributingFactor**: A single element in the explanation (e.g., "EUR/GBP volatility increased 28%").
- **ExplanationEnvelope**: The structured response containing factors, provenance, and confidence.

---

## Requirements

### Requirement 1: Multi-Factor Risk Explanation

**User Story:** As a risk manager, I want to understand why a trade's risk
score changed, so that I can assess whether the change is expected or requires
action.

#### Acceptance Criteria

1. THE agent SHALL accept a trade identifier (e.g., `FX-000042`) and retrieve
   the current and previous `RiskResult` via `AGENT_TOOL_PROTOCOL`.
2. THE agent SHALL retrieve the `RuleTrace` showing which `RULES_ENGINE` rules
   fired and their contribution to the score change.
3. THE agent SHALL retrieve the relevant `MarketSnapshot` to identify
   market-data-driven factors (rate moves, volatility shifts).
4. THE agent SHALL produce an `ExplanationEnvelope` listing each
   `ContributingFactor` with: factor type, magnitude, direction, and source.
5. THE agent SHALL rank contributing factors by magnitude of impact.
6. THE agent SHALL NOT invent factors — all explanations must trace to
   deterministic service outputs.

---

### Requirement 2: Rule Trace Readability

**User Story:** As a trader, I want rule-trace details translated into plain
business language, so that I can understand the explanation without knowing
the rule engine internals.

#### Acceptance Criteria

1. THE agent SHALL use the `PerceptionModel` (lightweight tier) to translate
   raw rule identifiers and technical trace output into business-readable
   language.
2. THE translation SHALL preserve the rule version, activation conditions,
   and impact value from the trace.
3. WHEN multiple rules fire for the same trade, THE agent SHALL explain
   rule interaction (cumulative vs overriding effects).
4. THE agent SHALL include the rule version that fired and note if it differs
   from the prior calculation's rule version.

---

### Requirement 3: Follow-Up Question Support

**User Story:** As a risk manager, I want to ask follow-up questions about
the risk explanation, so that I can drill into specific factors without
re-stating context.

#### Acceptance Criteria

1. THE agent SHALL maintain conversation context via the `SupervisorAgent`
   session memory for multi-turn follow-ups.
2. WHEN the user asks "why did the market factor change?" after an initial
   explanation, THE agent SHALL resolve the reference and provide deeper
   market data context.
3. THE agent SHALL support comparison queries ("compare risk on FX-000042
   between yesterday and today").
4. THE agent SHALL retrieve limit configuration when the user asks about
   limit breaches.

---

### Requirement 4: Similar Historical Cases

**User Story:** As a risk analyst, I want to see similar past risk changes,
so that I can assess whether this pattern is known or novel.

#### Acceptance Criteria

1. THE agent SHALL use embedding-based retrieval to find similar prior risk
   explanations from the episodic memory store.
2. THE agent SHALL present at most 3 similar cases with their outcomes.
3. THE similarity search SHALL consider: currency pair, factor type, magnitude,
   and rule version.

---

## Risk Classification

- **Inherent risk:** L (read/explain only — no side effects)
- **HITL requirement:** None (read-only agent)

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Perception | Lightweight (Haiku-class) | Rule trace → readable business language |
| Reasoning | Deep (Opus-class) | Multi-factor causal explanation, follow-up handling |
| Embedding | Embedding model | Similar-case retrieval from episodic store |
| Detection | Python sidecar | Market-deviation detector (time-series) |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getRiskResult(tradeId)` | risk-calculation-mcp | L | Current + previous risk scores, contributing factors, rules fired |
| `getRuleTrace(tradeId)` | risk-calculation-mcp | L | Detailed rule execution trace |
| `getMarketSnapshot(pair, timestamp)` | market-data-mcp | L | Point-in-time rates and volatility |
| `getLimitConfig(book, pair)` | risk-calculation-mcp | L | Applicable limit thresholds |
| `getTradeCharacteristics(tradeId)` | trade-lifecycle-mcp | L | Trade attributes (pair, notional, book, counterparty) |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| RISK-EVAL-01 | "Why did risk increase on FX-000042?" | Returns multi-factor explanation: rule change + market move |
| RISK-EVAL-02 | "What rules fired?" (follow-up) | Returns business-readable rule trace with versions |
| RISK-EVAL-03 | "Compare FX-000042 risk yesterday vs today" | Shows delta with per-factor breakdown |
| RISK-EVAL-04 | "Has this happened before?" | Returns up to 3 similar historical cases |
| RISK-EVAL-05 | "Why is EUR/GBP limit breached for book B17?" | Explains limit config vs current exposure |
| RISK-EVAL-06 | Trade with no risk change | "No material risk change detected for FX-000099" |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP tool calls to risk, market data, trade services |
| 8 — Memory Management | Session context for follow-ups; episodic for similar cases |
| 13 — RAG | Embedding retrieval for similar historical explanations |
| 16 — Reasoning | Multi-factor causal explanation synthesis |

---

## Python Sidecar Dependency

- **market-deviation-detector**: Emits anomaly envelope when a currency pair's
  volatility or rate deviates beyond baseline. NOT a trigger for this agent
  (on-demand only) but provides pre-computed deviation context.
