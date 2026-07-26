# Requirements Document — Distributed-Trace Latency Explanation Agent

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.
> Inherits `architecture-golden-path/01-service-nfrs` for cross-cutting NFRs.

## Introduction

This feature defines the **Distributed-Trace Latency Explanation Agent** — a
specialized agent that explains why a specific trade breached its per-stage
SLA latency target. It decomposes the distributed trace into spans, identifies
the slow stage, correlates with infrastructure state, and produces a
human-readable root cause explanation.

This agent is implemented as an `AGENT_PLATFORM` workflow export. It does NOT
fix latency — it explains latency root causes for investigation.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **TraceLatencyAgent**: The `AGENT_PLATFORM` workflow explaining per-trade latency.
- **DistributedTrace**: The full span tree for a trade's journey through the platform.
- **SpanBreakdown**: Per-span timing showing where time was spent.
- **SLABreach**: When a span or total trade processing exceeds the defined latency target.
- **ServiceBaseline**: The normal latency for a given span/service under typical conditions.

---

## Requirements

### Requirement 1: Trace Decomposition

**User Story:** As a platform engineer, I want to see exactly where time was
spent in a trade's processing, so that I can identify the slow stage.

#### Acceptance Criteria

1. THE agent SHALL accept a trade ID and retrieve its full distributed trace
   via `getTradeTrace(tradeId)`.
2. THE agent SHALL decompose the trace into per-span timing via
   `getSpanBreakdown()`.
3. THE agent SHALL identify spans that exceed their individual SLA targets.
4. THE agent SHALL present timing as a waterfall showing sequential and
   parallel stages.

---

### Requirement 2: Root Cause Identification

**User Story:** As an SRE, I want to understand WHY a specific stage was slow,
not just that it was slow.

#### Acceptance Criteria

1. THE agent SHALL retrieve service baseline latency via
   `getServiceBaseline(span)` and compare to actual.
2. THE agent SHALL call `correlateToDeploy()` to check if a recent deployment
   caused the slowdown.
3. THE agent SHALL identify infrastructure factors: connection pool exhaustion,
   GC pauses, downstream dependency latency, lock contention.
4. THE agent SHALL use the `ReasoningModel` to synthesize a root cause chain
   (e.g., "Redis call slow → enrichment stalled → missed cutoff").

---

### Requirement 3: Follow-Up and Comparison

**User Story:** As an engineer, I want to compare a slow trade's trace against
a normal trace, so that I can see exactly what diverged.

#### Acceptance Criteria

1. THE agent SHALL support comparison queries ("compare this trace to normal
   for the same service").
2. THE agent SHALL highlight spans where the delta is significant.
3. THE agent SHALL maintain context via Supervisor session for follow-ups.

---

## Risk Classification

- **Inherent risk:** L (read/explain only — no operational changes)
- **HITL requirement:** None (advisory agent)

---

## Model Tiers

| Role | Tier | Usage |
|------|------|-------|
| Perception | Lightweight (Haiku-class) | Span tree → structured facts |
| Detection | Python sidecar | Trace ingestion + baseline stats |
| Reasoning | Deep (Opus-class) | Root cause chain explanation |
| Memory | Episodic | Similar slow-trade patterns |

---

## MCP Tools Called

| Tool | Source Service | Risk | Purpose |
|------|---------------|------|---------|
| `getTradeTrace(tradeId)` | trace-mcp | L | Full distributed trace |
| `getSpanBreakdown()` | trace-mcp | L | Per-span timing |
| `getServiceBaseline(span)` | trace-mcp | L | Normal latency for comparison |
| `correlateToDeploy()` | change-mcp | L | Deployment correlation |

---

## Domain Acceptance Scenarios (Golden-Set Eval Harness)

| Scenario ID | Input | Expected Behavior |
|---|---|---|
| TRACE-EVAL-01 | "Why was FX-000042 slow?" | Identifies slow span (Redis → enrichment) |
| TRACE-EVAL-02 | Trade within SLA | "No SLA breach — all spans within target" |
| TRACE-EVAL-03 | "Compare to normal trace" | Shows delta at each span |
| TRACE-EVAL-04 | Multiple slow spans | Identifies primary bottleneck |
| TRACE-EVAL-05 | Deploy correlation | "Latency started after deploy X" |
| TRACE-EVAL-06 | "Why did this miss the cutoff?" | Full chain from slow span to missed deadline |

---

## Agentic Design Patterns

| Pattern | Application |
|---|---|
| 5 — Tool Use | MCP calls to trace and change services |
| 8 — Memory Management | Similar slow-trace patterns |
| 16 — Reasoning | Root cause chain synthesis |

---

## Python Sidecar Dependency

- **trace-ingestion-sidecar**: Ingests distributed traces, computes per-service
  baseline latency statistics, and emits SLA-breach envelopes when thresholds
  exceeded.
