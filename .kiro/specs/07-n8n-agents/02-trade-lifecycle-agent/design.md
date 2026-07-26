# Design — Trade Lifecycle Reconstruction Agent

## 1. Overview

The Trade Lifecycle Reconstruction Agent reconstructs a trade's complete business journey on demand. Given a trade ID, it retrieves the full event timeline via MCP tools, detects lifecycle anomalies (missing, duplicate, out-of-order events), identifies the probable cause of deviations, retrieves similar past failures, and recommends safe next actions from the permitted actions catalogue.

**Trigger mechanism:** Webhook (invoked by Supervisor Agent or direct API call).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Get Trade (MCP)] → [Get Trade Events (MCP)] → [Get Trade Timeline (MCP)]
                                                                              │
                                                                              ▼
                                                              [LLM Anomaly Detection & Analysis]
                                                                              │
                                                                              ▼
                                                              [Similar Failure Retrieval (HTTP)]
                                                                              │
                                                                              ▼
                                                              [LLM Reasoning: Probable Cause + Recommendation]
                                                                              │
                                                                              ▼
                                                              [Format AgentEnvelope Response]
                                                                              │
                                                                              ▼
                                                              [Respond to Webhook]
```

### Node-by-Node Description:

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives POST with `{ tradeId, correlationId }` |
| 2 | Get Trade | `n8n-nodes-base.httpRequest` | MCP tool call: `getTrade` on trade-lifecycle-service |
| 3 | Error Check: getTrade | `n8n-nodes-base.if` | Checks if getTrade returned FAILURE (404) |
| 4 | Get Trade Events | `n8n-nodes-base.httpRequest` | MCP tool call: `getTradeEvents` on trade-lifecycle-service |
| 5 | Get Trade Timeline | `n8n-nodes-base.httpRequest` | MCP tool call: `getTradeTimeline` on trade-lifecycle-service |
| 6 | Anomaly Detection (LLM) | `@n8n/n8n-nodes-langchain.agent` | Lightweight perception model: extract anomalies from timeline |
| 7 | Similar Failure Retrieval | `n8n-nodes-base.httpRequest` | Vector store query for similar past failures |
| 8 | Reasoning & Recommendation (LLM) | `@n8n/n8n-nodes-langchain.agent` | Deep reasoning: probable cause + safe actions |
| 9 | Format Response | `n8n-nodes-base.set` | Formats AgentEnvelope with facts, violations, permittedActions |
| 10 | Respond to Webhook | `n8n-nodes-base.respondToWebhook` | Returns AgentEnvelope to caller |
| 11 | Error Response | `n8n-nodes-base.respondToWebhook` | Returns error for trade not found |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/trade-lifecycle`
- **Method:** POST
- **Request body:**
  ```json
  {
    "tradeId": "FX-000042",
    "correlationId": "corr-xyz789",
    "userId": "user-ops-01"
  }
  ```

---

## 4. MCP Tool Calls

| Step | Tool | Endpoint | Input | Output |
|------|------|----------|-------|--------|
| 1 | `getTrade` | `http://trade-lifecycle-service:8081/mcp/getTrade` | `{ "tradeId": "FX-000042" }` | Trade state: status, region, currencyPair, timestamps |
| 2 | `getTradeEvents` | `http://trade-lifecycle-service:8081/mcp/getTradeEvents` | `{ "tradeId": "FX-000042" }` | Array of domain events with timestamps |
| 3 | `getTradeTimeline` | `http://trade-lifecycle-service:8081/mcp/getTradeTimeline` | `{ "tradeId": "FX-000042" }` | Ordered timeline with stage annotations, SLA markers |

Data flows sequentially: getTrade establishes current state → getTradeEvents provides raw event history → getTradeTimeline provides the annotated, ordered view that the LLM reasons over.

---

## 5. LLM Node Configuration

### Anomaly Detection (Node 6)
- **Model tier:** Lightweight perception (Haiku-class)
- **System prompt summary:** "You are a trade lifecycle anomaly detector. Given a trade's expected lifecycle stages and observed timeline, identify: missing events (expected but not seen within SLA), duplicated events (same type observed twice), out-of-order events (arrived before prerequisite), and unexpected terminal states. Return structured JSON."
- **Output structure:**
  ```json
  {
    "anomalies": [
      {
        "type": "MISSING_EVENT",
        "expectedStage": "ENRICHED",
        "observedState": "VALIDATED",
        "elapsedMinutes": 45,
        "slaMet": false
      }
    ],
    "isNormal": false,
    "currentStage": "VALIDATED",
    "expectedNextEvent": "ENRICHED"
  }
  ```
- **Temperature:** 0

### Reasoning & Recommendation (Node 8)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt summary:** "You are a causal analyst for FX trade lifecycle failures. Given the trade state, timeline, detected anomalies, and similar past failures, determine the probable cause (which service, rule, or condition caused the deviation). Recommend safe next actions ONLY from the permittedActions provided by the tool envelope. Never invent actions. Assign confidence (high/medium/low)."
- **Output structure:**
  ```json
  {
    "probableCause": {
      "service": "enrichment-service",
      "reason": "Service received trade but did not emit ENRICHED event within SLA",
      "confidence": "high"
    },
    "recommendedActions": [...],
    "explanation": "..."
  }
  ```
- **Temperature:** 0.2

---

## 6. Memory/Session

- **Within-run:** Sequential data accumulation — each MCP tool call builds the context for the next node. No multi-turn memory needed (single-shot analysis).
- **Episodic memory:** After completing diagnosis, the result (trade ID, anomaly type, probable cause, resolution) is written to PostgreSQL episodic store for future similar-failure retrieval.
- **Vector store:** Embedding of diagnosed cases for top-K similarity search by the Similar Failure Retrieval node.

---

## 7. HITL Gate

- **This agent has NO HITL gate.** It is read-only (Risk L).
- When recommending actions with Risk M/H (e.g., "request reprocessing"), the agent explicitly states that human approval is required and that execution occurs via the Recovery Agent — NOT this agent.
- HITL enforcement happens at the Supervisor level when the user attempts to execute a recommended action.

---

## 8. Error Handling

| Failure Mode | Handling |
|---|---|
| `getTrade` returns 404 (trade not found) | Immediately return error response: "Trade FX-XXXXXX not found" |
| `getTradeEvents` fails | Return partial analysis with warning: "Event history unavailable" |
| `getTradeTimeline` fails | Proceed with raw events from getTradeEvents; note degraded analysis |
| LLM anomaly detection returns malformed JSON | Retry once; if fails, return raw tool data without analysis |
| Vector store unreachable | Skip similar failure retrieval; note "no similar cases available" |
| LLM reasoning fails | Return anomaly detection results without probable cause |

---

## 9. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| TLC-EVAL-01 | Happy path | FX-000042 (fully settled) | Complete timeline; no anomalies; SETTLED state confirmed |
| TLC-EVAL-02 | Missing event | FX-000101 (stuck at VALIDATED) | Missing-event anomaly at ENRICHED; enrichment-service probable cause |
| TLC-EVAL-03 | Duplicate event | FX-000205 (duplicate RISK_CALCULATED) | Duplicate-event anomaly detected; classifies as replay vs true duplicate |
| TLC-EVAL-04 | Out-of-order | FX-000333 (BOOKED before RISK_CALCULATED) | Out-of-order anomaly; booking service identified |
| TLC-EVAL-05 | Unexpected terminal | FX-000500 (FAILED) | Terminal state anomaly; shows similar failures; recommends escalation |
| TLC-EVAL-06 | Trade not found | FX-999999 | Clean error response: "trade not found" |

---

## 10. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Trade Data Retrieval via MCP Tools | Nodes 2, 4, 5 (sequential getTrade → getTradeEvents → getTradeTimeline) |
| Rq2: Lifecycle Anomaly Detection | Node 6 (Perception LLM with structured anomaly output) |
| Rq3: Probable Cause Identification | Node 8 (Deep reasoning LLM with grounded analysis) |
| Rq4: Safe Action Recommendation | Node 8 outputs permittedActions from tool envelope only |
| Rq5: Similar Failure Retrieval | Node 7 (Vector store HTTP query, top-3) |
| Rq6: Model Tier Allocation | Perception (Haiku) for Node 6, Deep reasoning (Opus) for Node 8 |
