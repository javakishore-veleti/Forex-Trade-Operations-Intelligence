# Design — Market-Data Feed Staleness Agent

## 1. Overview

The Market-Data Feed Staleness Agent detects stale feeds and crossed quotes,
then gates risk calculations for contaminated pairs. Risk M with HITL on
block actions.

**Trigger mechanism:** Webhook (scheduled probe or tick-gap sidecar alert).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Get Stale Pairs] → [Detect Crossed Quotes]
                                                   │
                                                   ▼
                                    [Get Downstream Dependencies]
                                                   │
                                                   ▼
                                    [LLM: Impact Analyzer]
                                                   │
                                                   ▼
                                    [IF: Block Needed?]
                                             │          │
                                        yes          no
                                             │          │
                                 [HITL: Block Approval]  │
                                     │        │          │
                                approved  denied         │
                                     │        │          │
                          [Block Risk Calc]  [Alert]     │
                                     │        │          │
                                     └────────┴──────────┘
                                                   │
                                                   ▼
                                    [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ pairs, region, correlationId }` |
| 2 | Get Stale Pairs | `n8n-nodes-base.httpRequest` | GET `getStalePairs()` via MCP |
| 3 | Detect Crossed Quotes | `n8n-nodes-base.httpRequest` | GET `detectCrossedQuote()` via MCP |
| 4 | Get Downstream Dependencies | `n8n-nodes-base.httpRequest` | GET `getDownstreamRiskDependency(pair)` |
| 5 | Impact Analyzer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: analyze impact + recommend |
| 6 | Block Check (IF) | `n8n-nodes-base.if` | Branch if block recommended |
| 7 | Block Approval Gate (Wait) | `n8n-nodes-base.wait` | HITL for block approval |
| 8 | Approval Handler (IF) | `n8n-nodes-base.if` | Branch approved/denied |
| 9 | Block Risk Calc | `n8n-nodes-base.httpRequest` | POST `blockRiskCalc(pair)` |
| 10 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns staleness report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/market-data-staleness`
- **Method:** POST
- **Request body:**
  ```json
  {
    "pairs": ["EUR/USD", "GBP/JPY"],
    "region": "EMEA",
    "correlationId": "corr-mkt-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `getStalePairs()` | region | pairs exceeding threshold |
| 2 | `detectCrossedQuote()` | region | crossed-quote pairs |
| 3 | `getDownstreamRiskDependency(pair)` | stale/crossed pairs | affected trades/processes |
| 4 (gated) | `blockRiskCalc(pair)` | pair | block confirmation |

Steps 1-2 execute in parallel.

---

## 5. LLM Node Configuration

### Impact Analyzer (Node 5)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a market-data quality analyst for an FX platform. Given: (1) stale pairs with last-tick ages, (2) crossed-quote pairs, (3) downstream risk dependencies — assess business impact. Distinguish stale-during-trading-hours from expected silence. Group affected pairs by feed source if possible. Recommend blockRiskCalc for pairs where staleness > critical threshold and trades are pending. Output JSON: `{ stalePairs[], crossedPairs[], affectedTrades[], blockRecommendation[], severity, summary }`."
- **Temperature:** 0

---

## 6. HITL Gate

- **Placement:** After impact analysis (Node 7)
- **Condition:** Block recommended for any pair
- **Wait node:** Resume on `POST /webhook/mkt-block-approval/{executionId}`
- **Timeout:** 15 minutes (time-critical for risk calc window)
- **Approval payload:**
  ```json
  {
    "executionId": "exec-mkt-001",
    "decision": "APPROVED",
    "approverUserId": "user-risk-01",
    "pairs": ["EUR/USD"]
  }
  ```

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Market-data service unavailable | Treat as ALL_STALE; escalate immediately |
| Downstream dependency query fails | Block without dependency detail; note gap |
| Business calendar unavailable | Conservative: treat silence as stale |
| Block request fails | Retry once; fallback to manual alert |
| No stale pairs | Return "All feeds within freshness SLA" |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| MKT-EVAL-01 | Staleness | EUR/USD stale during London | Block proposed |
| MKT-EVAL-02 | HITL-approve | Block approved | Calls blockRiskCalc |
| MKT-EVAL-03 | Crossed | GBP/JPY bid > ask | Flagged + affected trades |
| MKT-EVAL-04 | Calendar | Silent during market close | No alert |
| MKT-EVAL-05 | Healthy | All fresh | "Within SLA" |
| MKT-EVAL-06 | Multi-pair | Same source stale | Groups by provider |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Feed Freshness Detection | Nodes 2 (stale pairs) |
| Rq2: Crossed-Quote Detection | Node 3 (crossed quotes) |
| Rq3: Risk Calc Blocking Gate | Nodes 5-9 (analysis + gate + block) |
