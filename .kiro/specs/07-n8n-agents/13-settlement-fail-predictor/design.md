# Design — Settlement-Fail Prediction Agent

## 1. Overview

The Settlement-Fail Predictor identifies trades at risk of settlement failure
using ML probability scoring + deterministic checks (SSI, nostro). It ranks
at-risk trades and escalates via HITL gate. Risk H.

**Trigger mechanism:** Webhook (scheduled pre-settlement sweep or on-demand).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Get Approaching Settlements] → [Loop: Per Trade]
                                                          │
                                                    [Check Missing SSI]
                                                          │
                                                    [Check Nostro Shortfall]
                                                          │
                                                    [Predict Fail Probability]
                                                          │
                                                    [End Loop]
                                                          │
                                                          ▼
                                          [LLM: Risk Ranker & Explainer]
                                                          │
                                                          ▼
                                          [IF: High-Risk Exist?]
                                                   │          │
                                              yes          no
                                                   │          │
                                       [HITL: Escalation]     │
                                           │        │         │
                                      approved  denied        │
                                           │        │         │
                                [Escalate Risk]  [Hold]       │
                                           │        │         │
                                           └────────┴─────────┘
                                                          │
                                                          ▼
                                          [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ window, region }` |
| 2 | Get Approaching Settlements | `n8n-nodes-base.httpRequest` | GET trades in settlement window |
| 3 | Loop Trades | `n8n-nodes-base.splitInBatches` | Iterate per trade |
| 4 | Check Missing SSI | `n8n-nodes-base.httpRequest` | GET `getMissingSSI(tradeId)` |
| 5 | Check Nostro Shortfall | `n8n-nodes-base.httpRequest` | GET `getNostroShortfall(ccy)` |
| 6 | Predict Fail Probability | `n8n-nodes-base.httpRequest` | GET `predictFailProbability()` |
| 7 | Risk Ranker & Explainer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: rank + explain |
| 8 | High-Risk Check (IF) | `n8n-nodes-base.if` | Branch if any probability > 0.7 |
| 9 | Escalation Gate (Wait) | `n8n-nodes-base.wait` | HITL for escalation approval |
| 10 | Escalation Handler (IF) | `n8n-nodes-base.if` | Branch approved/denied |
| 11 | Escalate Risk | `n8n-nodes-base.httpRequest` | POST `escalateSettlementRisk()` |
| 12 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns risk report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/settlement-fail-predictor`
- **Method:** POST
- **Request body:**
  ```json
  {
    "window": "T-1",
    "region": "GLOBAL",
    "correlationId": "corr-sett-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | (settlement query) | window, region | trades approaching settlement |
| 2 (per trade) | `getMissingSSI(tradeId)` | tradeId | SSI status |
| 3 (per trade) | `getNostroShortfall(ccy)` | currency | shortfall amount |
| 4 (per trade) | `predictFailProbability()` | trade features | probability |
| 5 (gated) | `escalateSettlementRisk()` | escalation package | confirmation |

---

## 5. LLM Node Configuration

### Risk Ranker & Explainer (Node 7)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a settlement risk analyst. Given trades with fail probabilities, SSI status, and nostro data — produce a prioritized risk report. Rank by: probability, amount, deadline proximity. Group by reason type (missing SSI, nostro shortfall, counterparty history). Identify clusters (e.g., 'all JPY trades for FX-CP-003'). Never invent data. Output as EscalationPackage JSON."
- **Temperature:** 0.1

---

## 6. HITL Gate

- **Placement:** After risk ranking (Node 9)
- **Condition:** Any trade with fail probability > 0.7
- **Wait node:** Resume on `POST /webhook/settlement-escalation/{executionId}`
- **Timeout:** 4 hours (before settlement deadline)
- **Approval payload:**
  ```json
  {
    "executionId": "exec-sett-001",
    "decision": "APPROVED",
    "approverUserId": "user-settle-01",
    "tradeIds": ["FX-000042", "FX-000043"]
  }
  ```

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Settlement service unavailable | Report "unable to sweep"; alert ops |
| ML model timeout | Use deterministic checks only; note incomplete |
| SSI check fails | Flag trade as UNKNOWN_SSI; include in report |
| Escalation call fails | Retry once; fallback to alert notification |
| No trades in window | Return "No settlements approaching" |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| SETT-EVAL-01 | Integration | 3 at-risk trades | Ranked report |
| SETT-EVAL-02 | Missing SSI | Trade without SSI | Identified + explained |
| SETT-EVAL-03 | Cluster | JPY nostro shortfall | Grouped JPY trades |
| SETT-EVAL-04 | HITL-approve | Escalation approved | Calls escalate |
| SETT-EVAL-05 | Healthy | All trades OK | "No at-risk" |
| SETT-EVAL-06 | Low-risk | All probability < 0.3 | Reports, no escalation |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Pre-Settlement Sweep | Nodes 2-6 (fetch + loop + checks) |
| Rq2: Prioritized Ranking | Node 7 (LLM ranker) |
| Rq3: Escalation + HITL | Nodes 8-11 (check + gate + escalate) |
