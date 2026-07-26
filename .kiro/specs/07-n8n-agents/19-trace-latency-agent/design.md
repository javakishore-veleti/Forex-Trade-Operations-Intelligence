# Design — Distributed-Trace Latency Explanation Agent

## 1. Overview

The Trace Latency Agent explains per-trade latency by decomposing distributed
traces, comparing spans to baselines, correlating with deploys, and producing
root cause chains. Risk L — advisory only.

**Trigger mechanism:** Webhook (SLA breach from sidecar or on-demand query via Supervisor).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Get Trade Trace] → [Get Span Breakdown] → [Get Service Baselines]
                                                                         │
                                                                         ▼
                                                         [Correlate to Deploy]
                                                                         │
                                                                         ▼
                                                         [LLM: Trace Perception (Haiku)]
                                                                         │
                                                                         ▼
                                                         [LLM: Root Cause Synthesizer (Opus)]
                                                                         │
                                                                         ▼
                                                         [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ tradeId, question }` |
| 2 | Get Trade Trace | `n8n-nodes-base.httpRequest` | GET `getTradeTrace(tradeId)` |
| 3 | Get Span Breakdown | `n8n-nodes-base.httpRequest` | GET `getSpanBreakdown()` |
| 4 | Get Service Baselines | `n8n-nodes-base.httpRequest` | GET `getServiceBaseline(span)` per slow span |
| 5 | Correlate to Deploy | `n8n-nodes-base.httpRequest` | GET `correlateToDeploy()` |
| 6 | Trace Perception (LLM) | `@n8n/n8n-nodes-langchain.agent` | Haiku: span tree → structured facts |
| 7 | Root Cause Synthesizer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: root cause chain |
| 8 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns latency report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/trace-latency`
- **Method:** POST
- **Request body:**
  ```json
  {
    "tradeId": "FX-000042",
    "question": "Why was this trade slow?",
    "slaBreach": {
      "totalMs": 4520,
      "targetMs": 2000,
      "slowestSpan": "enrichment-service"
    },
    "correlationId": "corr-trace-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `getTradeTrace(tradeId)` | tradeId | full span tree |
| 2 | `getSpanBreakdown()` | traceId | per-span timing |
| 3 | `getServiceBaseline(span)` | service name | normal latency stats |
| 4 | `correlateToDeploy()` | service + timeframe | deployment correlation |

Steps 3-4 depend on step 2 output (to identify slow spans).

---

## 5. LLM Node Configuration

### Trace Perception (Node 6)
- **Model tier:** Lightweight (Haiku-class)
- **System prompt:** "Parse the span tree and breakdown into structured facts. For each span: service name, duration_ms, is_slow (exceeds baseline), parent span. Identify the critical path. Output as JSON array of SpanFact objects."
- **Temperature:** 0

### Root Cause Synthesizer (Node 7)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a distributed systems latency analyst. Given: structured span facts, service baselines, and deployment correlation — produce a root cause chain. Format: 'X slow → Y stalled → Z missed cutoff'. Include: (1) primary bottleneck, (2) infrastructure factor (pool exhaustion, GC, dependency), (3) whether correlated to a deploy. If no breach, state 'all spans within SLA'. Output as LatencyReport JSON with `rootCause`, `chain[]`, `deployCorrelation`, `recommendation`."
- **Temperature:** 0.2

---

## 6. HITL Gate

**Not applicable.** This agent is Risk L (advisory only).

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Trace not found | Return "No trace found for {tradeId}" |
| Span breakdown unavailable | Return trace-level summary only |
| Baseline service down | Compare without baseline; note limitation |
| Deploy correlation fails | Skip correlation; note in report |
| LLM malformed output | Retry once; return raw span data |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| TRACE-EVAL-01 | Slow trade | FX-000042 with Redis slow | Root cause: Redis → enrichment |
| TRACE-EVAL-02 | Normal | Trade within SLA | "No SLA breach" |
| TRACE-EVAL-03 | Comparison | "Compare to normal" | Delta per span |
| TRACE-EVAL-04 | Multiple slow | Multiple spans slow | Primary bottleneck |
| TRACE-EVAL-05 | Deploy | Deploy correlation | "Started after deploy X" |
| TRACE-EVAL-06 | Cutoff | Missed cutoff | Full chain explanation |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Trace Decomposition | Nodes 2-3 (trace + breakdown) + Node 6 (perception) |
| Rq2: Root Cause | Nodes 4-5 (baselines + deploy) + Node 7 (synthesizer) |
| Rq3: Comparison | Session context + follow-up support via Supervisor |
