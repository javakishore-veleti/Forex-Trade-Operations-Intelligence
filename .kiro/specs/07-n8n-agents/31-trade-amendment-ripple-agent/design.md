# Design — Trade Amendment Ripple Agent

## 1. Overview

The Trade Amendment Ripple Agent verifies that all downstream systems have
correctly reacted to a trade amendment or cancellation, and proposes
corrections for gaps. Risk M with HITL on corrective actions.

**Trigger mechanism:** Webhook (from TradeAmended/TradeCancelled event).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Find Downstream Effects] → [Check Risk Recalc]
                                                        │
                                                   [Check Settlement]
                                                        │
                                                   [Check Report]
                                                        │
                                                        ▼
                                          [LLM: Ripple Analyzer]
                                                        │
                                                        ▼
                                          [IF: Gaps Found?]
                                                   │          │
                                              yes          no
                                                   │          │
                                       [HITL: Correction]     │
                                           │        │         │
                                      approved  denied        │
                                           │        │         │
                                [Request Recalc]  [Hold]      │
                                           │        │         │
                                           └────────┴─────────┘
                                                        │
                                                        ▼
                                          [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ tradeId, eventType, correlationId }` |
| 2 | Find Downstream Effects | `n8n-nodes-base.httpRequest` | GET `findDownstreamEffects(tradeId)` |
| 3 | Check Risk Recalc | `n8n-nodes-base.httpRequest` | GET `checkRiskRecalcTriggered()` |
| 4 | Check Settlement | `n8n-nodes-base.httpRequest` | GET `checkSettlementWithdrawn()` |
| 5 | Check Report | `n8n-nodes-base.httpRequest` | GET `checkReportAmended()` |
| 6 | Ripple Analyzer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: gap explanation + impact |
| 7 | Gap Check (IF) | `n8n-nodes-base.if` | Branch if gaps detected |
| 8 | Correction Gate (Wait) | `n8n-nodes-base.wait` | HITL for correction approval |
| 9 | Approval Handler (IF) | `n8n-nodes-base.if` | Branch approved/denied |
| 10 | Request Recalc | `n8n-nodes-base.httpRequest` | POST `requestMissingRecalc()` |
| 11 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns ripple report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/trade-amendment-ripple`
- **Method:** POST
- **Request body:**
  ```json
  {
    "tradeId": "FX-000042",
    "eventType": "AMENDED",
    "correlationId": "corr-ripple-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `findDownstreamEffects` | tradeId | expected effects list |
| 2 | `checkRiskRecalcTriggered` | tradeId | risk status |
| 3 | `checkSettlementWithdrawn` | tradeId | settlement status |
| 4 | `checkReportAmended` | tradeId | report status |
| 5 (gated) | `requestMissingRecalc` | tradeId, gapType | recalc confirmation |

Steps 2-4 execute in parallel after step 1.

---

## 5. LLM Node Configuration

### Ripple Analyzer (Node 6)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a trade operations analyst for an FX platform. Given: (1) expected downstream effects, (2) check results for risk/settlement/reporting — identify gaps where expected reactions did not occur. For each gap: explain likely cause (lag/missing event/failure), quantify business impact, and recommend correction. Produce JSON: `{ tradeId, eventType, expectedEffects[], confirmedEffects[], gaps[], businessImpact, corrections[], summary }`."
- **Temperature:** 0

---

## 6. HITL Gate

- **Placement:** After gap detection (Node 8)
- **Condition:** Downstream gaps identified
- **Wait node:** Resume on `POST /webhook/ripple-correction/{executionId}`
- **Timeout:** 4 hours
- **Approval payload:**
  ```json
  {
    "executionId": "exec-ripple-001",
    "decision": "APPROVED",
    "approverUserId": "user-ops-01",
    "corrections": ["RISK_RECALC", "REPORT_AMEND"]
  }
  ```

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Graph traversal fails | Report "Unable to determine effects" |
| Check service unavailable | Flag as UNKNOWN status; escalate |
| Recalc request fails | Retry once; alert ops |
| All effects confirmed | Return "All downstream effects propagated" |
| Trade not found | Return "Trade not found" |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| RIPPLE-EVAL-01 | Full ripple | Trade amended, all OK | "All confirmed" |
| RIPPLE-EVAL-02 | Risk gap | Recalc missing | Gap + correction proposed |
| RIPPLE-EVAL-03 | Settlement | Not withdrawn | Gap + impact reported |
| RIPPLE-EVAL-04 | HITL-approve | Correction approved | Calls requestRecalc |
| RIPPLE-EVAL-05 | Cancelled | All effects clean | Full confirmation |
| RIPPLE-EVAL-06 | Report | Amendment not reflected | Reporting gap flagged |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Downstream Tracking | Nodes 2-5 (effects + checks) |
| Rq2: Gap Detection | Node 6 (LLM analysis) |
| Rq3: Corrective Action | Nodes 7-10 (check + gate + correct) |
