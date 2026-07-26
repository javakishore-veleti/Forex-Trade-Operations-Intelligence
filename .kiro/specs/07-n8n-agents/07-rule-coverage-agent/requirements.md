# Requirements Document — Currency-Pair Rule-Coverage Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Currency-Pair Rule-Coverage Agent** — a specialized
agent that monitors rule coverage across currency pairs, detects when exotic or
newly-traded pairs rely excessively on fallback/default rules, and identifies
coverage gaps that may lead to mispricing or unintended risk exposure.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
author rules — it analyzes the coverage matrix and fallback firing rates from
deterministic services.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **RuleCoverageAgent**: The `AGENT_PLATFORM` workflow monitoring pair-level rule coverage.
- **CoverageMatrix**: A mapping of currency pairs to specific vs fallback rules that apply.
- **FallbackFiringRate**: The percentage of decisions for a pair that rely on the generic fallback rule rather than a pair-specific rule.
- **UncoveredPair**: A currency pair with no specific rule, relying entirely on default logic.
- **RuleGap**: A scenario where a pair's characteristics make the fallback rule inappropriate.

---

## Requirements

### Requirement 1: Coverage Matrix Analysis

**User Story:** As a rules manager, I want to see which currency pairs have
specific rules vs relying on fallback, so that I can prioritize rule authoring.

#### Acceptance Criteria

1. THE agent SHALL retrieve the full coverage matrix via `getRuleCoverageMatrix()`.
2. THE agent SHALL categorize pairs as: fully-covered, partially-covered, or uncovered.
3. THE agent SHALL rank uncovered pairs by trading volume and risk materiality.
4. THE agent SHALL identify newly-traded pairs that have entered production
   without specific rule coverage.

---

### Requirement 2: Fallback Firing Rate Monitoring

**User Story:** As a risk analyst, I want to know when a pair's fallback
firing rate exceeds baseline, so that I can assess default-rule suitability.

#### Acceptance Criteria

1. THE agent SHALL be triggered by a Python sidecar anomaly envelope when
   fallback firing rate breaches baseline for any pair.
2. THE agent SHALL retrieve `getFallbackFiringRate(pair)` for the flagged pair.
3. THE agent SHALL compare current rate to historical baseline and seasonal norm.
4. THE agent SHALL assess whether the fallback rule produces appropriate
   results for the pair's characteristics via `simulateRuleGap()`.

---

### Requirement 3: Gap Explanation and Recommendation

**User Story:** As a rules analyst, I want the agent to explain why a coverage
gap matters and what kind of rule would address it.

#### Acceptance Criteria

1. THE agent SHALL use the `ReasoningModel` to explain the business risk of
   the gap (potential mispricing, unintended exposure).
2. THE agent SHALL reference the pair's trading characteristics (volatility,
   volume, counterparty types) in the explanation.
3. THE agent SHALL suggest the type of rule needed (not the rule itself —
   rule authoring is the Shadow Rule Simulator's domain).
4. THE agent SHALL NOT propose rule changes or deploy any rule.

---

## Risk Classification

- **Inherent risk:** L (read/explain only — no rule modifications)
- **HITL requirement:** None (advisory agent)

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Detection | Python sidecar | Fallback firing-rate anomaly detection |
| Reasoning | Deep (Opus-class) | Gap analysis, business risk explanation |
| Policy | Deterministic (RULES_ENGINE simulation) | Gap simulation |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getRuleCoverageMatrix()` | rules-engine-mcp | L | Full pair→rule mapping |
| `getFallbackFiringRate(pair)` | rules-engine-mcp | L | Current fallback usage rate |
| `getUncoveredPairs()` | rules-engine-mcp | L | Pairs with no specific rules |
| `simulateRuleGap(pair)` | rules-engine-mcp | L | Simulate fallback appropriateness |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| COV-EVAL-01 | "Show me uncovered pairs" | Returns ranked list of uncovered pairs |
| COV-EVAL-02 | Sidecar detects high fallback rate for USD/TRY | Explains gap + risk + rule type needed |
| COV-EVAL-03 | New pair FX-PAIR-XYZ enters production | Flags as uncovered, recommends rule authoring |
| COV-EVAL-04 | All pairs fully covered | "Full coverage — no gaps detected" |
| COV-EVAL-05 | "Is EUR/GBP properly covered?" | Shows specific rules + fallback usage |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP tool calls to rules engine |
| 10 — Goal Setting | Coverage improvement goal |
| 20 — Exploration | Discovering uncovered pairs |

---

## Python Sidecar Dependency

- **firing-rate-anomaly-detector**: Also monitors fallback-specific firing
  rates; emits anomaly envelope when baseline breached for a pair.
