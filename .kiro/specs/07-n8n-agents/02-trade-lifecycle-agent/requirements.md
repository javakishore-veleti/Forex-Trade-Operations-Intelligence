# Requirements Document — Trade Lifecycle Reconstruction Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Trade Lifecycle Reconstruction Agent** — a
specialized agent that reconstructs a trade's complete business journey on
demand. When a user or upstream agent asks "what happened to trade X?", this
agent retrieves the full event timeline, detects anomalies (missing events,
duplicates, out-of-order events), compares the trade's current state against
its expected lifecycle state, identifies the responsible service or business
rule for any deviation, and recommends the next safe action drawn from the
permitted actions catalogue.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It calls
typed `AGENT_TOOL_PROTOCOL` tools on the `trade-lifecycle-service` and related
services. It performs **read-only** analysis — it never executes state changes
without explicit delegation to a recovery agent and human approval.

All identifiers in examples use the synthetic `FX-` prefix. All organization
names are fictional.

---

## Glossary

- **TradeLifecycleAgent**: The `AGENT_PLATFORM` workflow that reconstructs a
  trade's business journey and diagnoses lifecycle anomalies.
- **TradeTimeline**: The ordered sequence of domain events for a given trade,
  as returned by the `getTradeTimeline` `AGENT_TOOL_PROTOCOL` tool.
- **ExpectedLifecycle**: The normative sequence of events a trade should
  traverse (e.g. CAPTURED, VALIDATED, ENRICHED, RISK_CALCULATED, BOOKED,
  SETTLED), as defined by the `trade-lifecycle-service` state machine.
- **LifecycleAnomaly**: A deviation between the `TradeTimeline` (observed) and
  the `ExpectedLifecycle` (normative): missing event, duplicated event,
  out-of-order event, or unexpected terminal state.
- **ProbableCause**: The agent's determination of which service, rule, or
  condition is responsible for a `LifecycleAnomaly`.
- **PermittedActions**: The set of safe next actions returned by the
  deterministic `SERVICE_FRAMEWORK` service (never authored by the LLM).
- **ToolEnvelope**: The standard `MCP_Tool_Contract` response wrapper (per
  `06-local-deploy/01-mcp-server-setup`).
- **PerceptionModel**: The LLM tier used for structured extraction of event
  payloads and log normalization (lightweight).
- **ReasoningModel**: The LLM tier used for causal analysis, anomaly
  explanation, and action recommendation synthesis (deep reasoning).
- **EmbeddingModel**: The embedding tier used for similar-failure retrieval
  from the episodic memory store.
- **SyntheticData**: Test/example data using only `FX-` prefixed identifiers
  and fictional names.

---

## Requirements

### Requirement 1: Trade Data Retrieval via MCP Tools

**User Story:** As an operations analyst, I want to retrieve a trade's full
state and event history through a single agent query, so that I do not need to
query multiple systems manually.

#### Acceptance Criteria

1. THE `TradeLifecycleAgent` SHALL call the `getTrade` `AGENT_TOOL_PROTOCOL`
   tool (on `trade-lifecycle-service`) to retrieve the current trade state,
   given a `tradeId`.
2. THE `TradeLifecycleAgent` SHALL call the `getTradeEvents`
   `AGENT_TOOL_PROTOCOL` tool (on `trade-lifecycle-service`) to retrieve the
   full list of domain events for the given `tradeId`.
3. THE `TradeLifecycleAgent` SHALL call the `getTradeTimeline`
   `AGENT_TOOL_PROTOCOL` tool (on `trade-lifecycle-service`) to retrieve the
   ordered timeline with stage annotations for the given `tradeId`.
4. WHEN any tool call returns `status = FAILURE`, THE `TradeLifecycleAgent`
   SHALL surface the failure to the user with the specific tool and error
   reason, and SHALL NOT proceed with partial analysis unless explicitly
   handling the degraded case.
5. ALL tool calls SHALL include the `correlationId` from the originating
   request (inherited GP-Rq-2).

---

### Requirement 2: Lifecycle Anomaly Detection

**User Story:** As an operations analyst, I want the agent to automatically
detect when a trade's event history deviates from the expected lifecycle, so
that I can identify problems without manually comparing timelines.

#### Acceptance Criteria

1. THE `TradeLifecycleAgent` SHALL compare the observed `TradeTimeline` events
   against the `ExpectedLifecycle` for the trade's type and region.
2. THE `TradeLifecycleAgent` SHALL detect and classify the following
   `LifecycleAnomaly` types:
   - **Missing event**: An expected event that has not occurred within the SLA
     window for its stage.
   - **Duplicated event**: The same event type observed more than once for the
     same trade (excluding legitimate replays with distinct idempotency keys).
   - **Out-of-order event**: An event that arrived before its prerequisite
     event in the lifecycle state machine.
   - **Unexpected terminal state**: The trade is in a terminal state
     (e.g. FAILED, CANCELLED) that was not the expected outcome.
3. FOR EACH detected `LifecycleAnomaly`, THE agent SHALL report: the anomaly
   type, the expected vs. observed state, the time elapsed, and the stage
   where the deviation occurred.
4. WHEN no anomalies are detected, THE `TradeLifecycleAgent` SHALL confirm
   that the trade's lifecycle is progressing normally and report the current
   stage plus expected next event.

---

### Requirement 3: Probable Cause Identification

**User Story:** As an operations analyst, I want the agent to identify which
service or business rule is likely responsible for a lifecycle deviation, so
that I can direct remediation to the correct team.

#### Acceptance Criteria

1. FOR EACH `LifecycleAnomaly`, THE `TradeLifecycleAgent` SHALL use the
   `ReasoningModel` (deep reasoning) to determine the `ProbableCause` based on:
   - Which service owns the lifecycle stage where the anomaly occurred.
   - Whether the `TradeTimeline` shows the responsible service received but
     did not emit the expected event.
   - Whether similar failures have been observed previously (via
     `EmbeddingModel` similar-failure retrieval).
2. THE `ProbableCause` analysis SHALL reference only facts present in the
   `ToolEnvelope` responses — the agent SHALL NOT hallucinate service names,
   error codes, or causes not grounded in retrieved data.
3. THE `TradeLifecycleAgent` SHALL present the `ProbableCause` with a
   confidence qualifier (`high`, `medium`, `low`) based on the evidence
   available.
4. WHEN evidence is insufficient to determine a probable cause, THE agent
   SHALL state that explicitly and recommend what additional data would help.

---

### Requirement 4: Safe Action Recommendation

**User Story:** As an operations analyst, I want the agent to recommend the
next safe action I can take to resolve a stuck or anomalous trade, so that I
have an actionable path forward without guessing.

#### Acceptance Criteria

1. THE `TradeLifecycleAgent` SHALL derive its recommended actions exclusively
   from the `permittedActions` field in the `ToolEnvelope` returned by the
   deterministic `SERVICE_FRAMEWORK` service — the LLM SHALL NOT invent or
   compose actions.
2. FOR EACH recommended action, THE agent SHALL explain:
   - What the action does (in business terms).
   - What risk level the action carries (`ToolRisk` L/M/H).
   - What preconditions must be met before executing.
3. WHEN the recommended action has `ToolRisk` M or H, THE agent SHALL
   explicitly state that human approval is required and SHALL NOT offer to
   execute it autonomously.
4. WHEN no `permittedActions` are available (empty list), THE agent SHALL
   recommend escalation to a human operator with the diagnostic context.
5. THE `TradeLifecycleAgent` SHALL present actions in priority order
   (safest / most common first).

---

### Requirement 5: Similar Failure Retrieval

**User Story:** As an operations analyst, I want the agent to show me similar
past failures, so that I can see how they were resolved and apply the same fix.

#### Acceptance Criteria

1. THE `TradeLifecycleAgent` SHALL use the `EmbeddingModel` to query a
   vector store of previously diagnosed lifecycle failures for the top-K
   (configurable, default 3) similar cases.
2. FOR EACH similar case retrieved, THE agent SHALL present: the trade ID
   (synthetic), the anomaly type, the root cause, and the resolution applied.
3. WHEN no similar cases are found (empty result or below similarity
   threshold), THE agent SHALL state that this appears to be a novel failure
   pattern.
4. THE similar-failure retrieval SHALL use only synthetic identifiers in its
   stored corpus (inherited GP-Rq-14).

---

### Requirement 6: Model Tier Allocation

**User Story:** As a platform architect, I want the Trade Lifecycle Agent to
use the appropriate LLM tier for each cognitive task, so that cost and latency
are optimized without sacrificing reasoning quality.

#### Acceptance Criteria

1. THE `TradeLifecycleAgent` SHALL use the **lightweight perception model**
   (`PerceptionModel`) for structured extraction from raw event payloads and
   log entries.
2. THE `TradeLifecycleAgent` SHALL use the **deep reasoning model**
   (`ReasoningModel`) for causal analysis, anomaly explanation, and action
   recommendation synthesis.
3. THE `TradeLifecycleAgent` SHALL use the **embedding model**
   (`EmbeddingModel`) for similar-failure vector retrieval.
4. THE `TradeLifecycleAgent` SHALL maintain episodic memory of diagnosed cases
   in the `RELATIONAL_STORE` role for future retrieval by the embedding tier.
5. THE `TradeLifecycleAgent` SHALL NOT use any LLM tier for computing trade
   state, lifecycle transitions, or permitted actions — these come exclusively
   from deterministic `SERVICE_FRAMEWORK` services (inherited GP-Rq-13).

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| TLC-EVAL-01 | "What happened to trade FX-000042?" (normal, fully settled) | Returns complete timeline; confirms no anomalies; reports SETTLED state |
| TLC-EVAL-02 | "What happened to trade FX-000101?" (stuck at VALIDATED, missing ENRICHED for 45 min) | Detects missing-event anomaly at ENRICHED stage; identifies enrichment-service as probable cause; recommends "request reprocessing" (Risk M, gated) |
| TLC-EVAL-03 | "What happened to trade FX-000205?" (duplicate RISK_CALCULATED events) | Detects duplicated-event anomaly; classifies as legitimate replay or true duplicate; recommends accordingly |
| TLC-EVAL-04 | "What happened to trade FX-000333?" (BOOKED arrived before RISK_CALCULATED) | Detects out-of-order anomaly; identifies booking service processed without risk; recommends hold/investigation |
| TLC-EVAL-05 | "What happened to trade FX-000500?" (trade FAILED unexpectedly) | Detects unexpected terminal state; provides probable cause; shows similar past failures; recommends escalation |
| TLC-EVAL-06 | Query for non-existent trade FX-999999 | Tool returns FAILURE (404); agent surfaces "trade not found" clearly |

---

## Agentic Design Patterns

| Pattern | Application in this Agent |
|---|---|
| 1 — Prompt Chaining | Sequential tool calls: getTrade, getTradeEvents, getTradeTimeline, analysis |
| 5 — Tool Use | Calls 3 MCP tools on trade-lifecycle-service |
| 8 — Memory Management | Episodic memory of diagnosed cases for future retrieval |
| 13 — RAG (Retrieval) | Similar-failure retrieval via embedding model |
| 16 — Reasoning | Deep causal analysis for probable cause and recommendation |
| 12 — Human-in-the-Loop | Recommends but never auto-executes M/H actions |

---

## Risk Classification

- **Risk level:** L (read/explain only)
- **Side effects:** None — all tool calls are `ToolRisk` L (read)
- **HITL requirement:** Not required for this agent's own actions; however,
  when recommending M/H actions, the agent explicitly states that human
  approval is needed (enforcement occurs in the executing agent)

---

## MCP Tools Called

| Tool Name | Service | Risk | Purpose |
|---|---|---|---|
| `getTrade` | `trade-lifecycle-service` | L | Retrieve current trade state |
| `getTradeEvents` | `trade-lifecycle-service` | L | Retrieve full event list for trade |
| `getTradeTimeline` | `trade-lifecycle-service` | L | Retrieve ordered timeline with stage annotations |

---

## Python Sidecar Dependency

- **Optional:** `log-normalizer` sidecar (from `06-local-deploy/02-python-sidecars`)
  may pre-process raw log payloads before the `PerceptionModel` extracts
  structured facts. This is a perception optimization, not a trigger.
- **Trigger:** None — this agent is triggered on-demand (user query or
  Supervisor delegation), not by a Python sidecar.
