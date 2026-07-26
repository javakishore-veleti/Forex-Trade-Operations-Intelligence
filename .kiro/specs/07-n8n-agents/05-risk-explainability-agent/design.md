# Design — Trade Risk Explainability Agent

## 1. Overview

The Trade Risk Explainability Agent answers "why did risk change?" questions by
gathering deterministic risk results, rule traces, and market data, then
synthesizing a multi-factor explanation. It is a read-only agent (Risk L) with
no HITL gate.

**Trigger mechanism:** Webhook (routed from Supervisor Agent on RISK_EXPLAIN intent).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Fetch Risk Result] → [Fetch Rule Trace] → [Fetch Market Snapshot]
                                                                        │
                                                                        ▼
                                                        [Fetch Trade Characteristics]
                                                                        │
                                                                        ▼
                                                        [LLM: Rule Trace Translator]
                                                                        │
                                                                        ▼
                                                        [Similar Case Retrieval]
                                                                        │
                                                                        ▼
                                                        [LLM: Explanation Synthesizer]
                                                                        │
                                                                        ▼
                                                        [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ tradeId, question, sessionContext }` |
| 2 | Fetch Risk Result | `n8n-nodes-base.httpRequest` | GET `getRiskResult(tradeId)` via MCP |
| 3 | Fetch Rule Trace | `n8n-nodes-base.httpRequest` | GET `getRuleTrace(tradeId)` via MCP |
| 4 | Fetch Market Snapshot | `n8n-nodes-base.httpRequest` | GET `getMarketSnapshot(pair, timestamp)` via MCP |
| 5 | Fetch Trade Characteristics | `n8n-nodes-base.httpRequest` | GET `getTradeCharacteristics(tradeId)` via MCP |
| 6 | Rule Trace Translator | `@n8n/n8n-nodes-langchain.agent` | Haiku-class: raw trace → business language |
| 7 | Similar Case Retrieval | `n8n-nodes-base.httpRequest` | Vector search for similar prior explanations |
| 8 | Explanation Synthesizer | `@n8n/n8n-nodes-langchain.agent` | Opus-class: multi-factor explanation |
| 9 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns ExplanationEnvelope |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/risk-explainability`
- **Method:** POST
- **Request body:**
  ```json
  {
    "tradeId": "FX-000042",
    "question": "Why did risk increase?",
    "sessionContext": { "priorEntities": ["FX-000042"], "turn": 2 },
    "correlationId": "corr-risk-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Endpoint | Input | Output |
|------|------|----------|-------|--------|
| 1 | `getRiskResult` | risk-calculation-mcp | `tradeId` | current/previous risk, contributing factors, rules fired |
| 2 | `getRuleTrace` | risk-calculation-mcp | `tradeId` | rule IDs, versions, activation conditions, outputs |
| 3 | `getMarketSnapshot` | market-data-mcp | `pair`, `timestamp` | rates, volatility, spread at calculation time |
| 4 | `getTradeCharacteristics` | trade-lifecycle-mcp | `tradeId` | pair, notional, book, counterparty, region |
| 5 | `getLimitConfig` | risk-calculation-mcp | `book`, `pair` | threshold values (only if limit breach) |

Steps 2–4 are independent and execute in parallel.

---

## 5. LLM Node Configuration

### Rule Trace Translator (Node 6)
- **Model tier:** Lightweight (Haiku-class)
- **System prompt:** "Translate the following rule trace into business-readable language. Preserve rule version, activation condition, and impact value. Do not invent information. Output JSON array of `{ ruleId, version, businessDescription, impact, direction }`."
- **Temperature:** 0

### Explanation Synthesizer (Node 8)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a risk explanation agent for an FX trade operations platform. Given: (1) risk results with contributing factors, (2) readable rule trace, (3) market snapshot, (4) trade characteristics, and (5) similar historical cases — produce a coherent multi-factor explanation. Rank factors by impact magnitude. Never invent facts beyond the provided data. Format as ExplanationEnvelope with `factors[]`, `summary`, `similarCases[]`, `provenance`."
- **Temperature:** 0.2

---

## 6. HITL Gate

**Not applicable.** This agent is Risk L (read/explain only). No actions are proposed or executed.

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Risk result not found (404) | Return "No risk calculation found for {tradeId}" |
| Rule trace unavailable | Proceed without rule detail; note gap in explanation |
| Market data service timeout | Explain risk without market factor; note incomplete |
| LLM returns malformed output | Retry once with stricter prompt; fallback to raw data |
| Trade ID not found | Return "Trade {tradeId} not found in system" |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| RISK-EVAL-01 | Integration | "Why did risk increase on FX-000042?" | Multi-factor explanation with rule + market |
| RISK-EVAL-02 | Follow-up | "What rules fired?" | Business-readable rule trace |
| RISK-EVAL-03 | Comparison | "Compare yesterday vs today" | Delta breakdown per factor |
| RISK-EVAL-04 | RAG | "Has this happened before?" | Up to 3 similar cases |
| RISK-EVAL-05 | Limit | "Why is limit breached?" | Limit config vs exposure |
| RISK-EVAL-06 | No-change | Trade with stable risk | "No material change" message |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Multi-Factor Explanation | Nodes 2-5 (data gathering) + Node 8 (synthesis) |
| Rq2: Rule Trace Readability | Node 6 (Haiku translator) |
| Rq3: Follow-Up Support | Session context in webhook payload; Supervisor memory |
| Rq4: Similar Historical Cases | Node 7 (vector retrieval) |
