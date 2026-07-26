# Design — Cutoff & Calendar Enforcement Agent

## 1. Overview

The Cutoff & Calendar Enforcement Agent detects post-cutoff events and
proposes holding trades for the next business day. Uses deterministic
calendar service for all date math. Risk M with HITL on hold action.

**Trigger mechanism:** Webhook (per-region watcher or scheduled sweep).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Get Regional Cutoff] → [Get Post-Cutoff Events]
                                                      │
                                                      ▼
                                       [Classify Booking Date]
                                                      │
                                                      ▼
                                       [Get Approaching Cutoff]
                                                      │
                                                      ▼
                                       [LLM: Cutoff Analyzer]
                                                      │
                                                      ▼
                                       [IF: Hold Needed?]
                                                │          │
                                           yes          no
                                                │          │
                                    [HITL: Hold Approval]   │
                                        │        │          │
                                   approved  denied         │
                                        │        │          │
                             [Hold For Next Day]  [Log]     │
                                        │        │          │
                                        └────────┴──────────┘
                                                      │
                                                      ▼
                                       [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ region, correlationId }` |
| 2 | Get Regional Cutoff | `n8n-nodes-base.httpRequest` | GET `getRegionalCutoff(region)` |
| 3 | Get Post-Cutoff Events | `n8n-nodes-base.httpRequest` | GET `getPostCutoffEvents()` |
| 4 | Classify Booking Date | `n8n-nodes-base.httpRequest` | POST `classifyBookingDate()` per event |
| 5 | Get Approaching Cutoff | `n8n-nodes-base.httpRequest` | GET `getTradesApproachingCutoff()` |
| 6 | Cutoff Analyzer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: impact + recommendation |
| 7 | Hold Check (IF) | `n8n-nodes-base.if` | Branch if post-cutoff trades found |
| 8 | Hold Approval Gate (Wait) | `n8n-nodes-base.wait` | HITL for hold approval |
| 9 | Approval Handler (IF) | `n8n-nodes-base.if` | Branch approved/denied |
| 10 | Hold For Next Day | `n8n-nodes-base.httpRequest` | POST `holdForNextDay()` |
| 11 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns cutoff report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/cutoff-calendar`
- **Method:** POST
- **Request body:**
  ```json
  {
    "region": "APAC",
    "correlationId": "corr-cutoff-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `getRegionalCutoff` | region | cutoff time (DST-aware) |
| 2 | `getPostCutoffEvents` | region | post-cutoff trades/events |
| 3 | `classifyBookingDate` | tradeId, timestamp | correct business date |
| 4 | `getTradesApproachingCutoff` | region | at-risk trades |
| 5 (gated) | `holdForNextDay` | tradeId | hold confirmation |

Steps 2 and 4 can run in parallel after step 1.

---

## 5. LLM Node Configuration

### Cutoff Analyzer (Node 6)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a cutoff enforcement analyst for an FX operations platform. Given: (1) regional cutoff time, (2) post-cutoff events with booking date classifications, (3) approaching trades — produce a cutoff report. Identify: trades that would settle on wrong date, DST/holiday edge cases, and approaching-cutoff risks. Recommend holdForNextDay for post-cutoff trades. Never override the calendar service's date classification. Output JSON: `{ postCutoffTrades[], approachingTrades[], holdRecommendations[], edgeCases[], summary }`."
- **Temperature:** 0

---

## 6. HITL Gate

- **Placement:** After cutoff analysis (Node 8)
- **Condition:** Post-cutoff trades identified
- **Wait node:** Resume on `POST /webhook/cutoff-hold-approval/{executionId}`
- **Timeout:** 2 hours
- **Approval payload:**
  ```json
  {
    "executionId": "exec-cutoff-001",
    "decision": "APPROVED",
    "approverUserId": "user-ops-01",
    "tradeIds": ["FX-000055", "FX-000056"]
  }
  ```

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Calendar service unavailable | Conservative: flag all borderline trades |
| Post-cutoff query fails | Return "Unable to assess"; alert ops |
| Hold request fails | Retry once; fallback to manual alert |
| LLM returns malformed output | Use raw classification; escalate |
| No post-cutoff events | Return "No cutoff violations detected" |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| CUT-EVAL-01 | Post-cutoff | Trade 5min after cutoff | Hold proposed |
| CUT-EVAL-02 | HITL-approve | Hold approved | Calls holdForNextDay |
| CUT-EVAL-03 | Approaching | Trade 10min before cutoff | Warning issued |
| CUT-EVAL-04 | DST | Transition day | Shifted cutoff applied |
| CUT-EVAL-05 | Holiday | Friday → Monday edge | Correct date |
| CUT-EVAL-06 | Clean | All trades before cutoff | "No violations" |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Post-Cutoff Detection | Nodes 2-4 (cutoff + events + classify) |
| Rq2: Approaching Warnings | Node 5 (approaching) + Node 6 (LLM) |
| Rq3: Hold Gate | Nodes 7-10 (check + gate + hold) |
