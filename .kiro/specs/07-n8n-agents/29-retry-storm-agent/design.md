# Design — Retry-Storm & Backpressure Agent

## 1. Overview

The Retry-Storm & Backpressure Agent detects retry amplification cascades,
traces the root-cause service, and proposes targeted backpressure via HITL
gate. Risk H.

**Trigger mechanism:** Webhook (from retry-rate alert or breaker-open cascade).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Get Retry Amplification] → [Get Open Breakers]
                                                        │
                                                        ▼
                                          [Get Cascade Path]
                                                        │
                                                        ▼
                                          [LLM: Root Cause Analyzer]
                                                        │
                                                        ▼
                                          [IF: Storm Confirmed?]
                                                   │          │
                                              yes          no
                                                   │          │
                                       [HITL: Backpressure]   │
                                           │        │         │
                                      approved  denied        │
                                           │        │         │
                                [Apply Backpressure] [Hold]   │
                                           │        │         │
                                           └────────┴─────────┘
                                                        │
                                                        ▼
                                          [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ service, alertType, correlationId }` |
| 2 | Get Retry Amplification | `n8n-nodes-base.httpRequest` | GET `getRetryAmplification()` |
| 3 | Get Open Breakers | `n8n-nodes-base.httpRequest` | GET `getOpenBreakers()` |
| 4 | Get Cascade Path | `n8n-nodes-base.httpRequest` | GET `getCascadePath(service)` |
| 5 | Root Cause Analyzer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: root vs symptom |
| 6 | Storm Check (IF) | `n8n-nodes-base.if` | Branch if storm confirmed |
| 7 | Backpressure Gate (Wait) | `n8n-nodes-base.wait` | HITL for backpressure approval |
| 8 | Approval Handler (IF) | `n8n-nodes-base.if` | Branch approved/denied |
| 9 | Apply Backpressure | `n8n-nodes-base.httpRequest` | POST `applyBackpressure()` or `tripBreaker()` |
| 10 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns storm report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/retry-storm`
- **Method:** POST
- **Request body:**
  ```json
  {
    "service": "enrichment-service",
    "alertType": "RETRY_AMPLIFICATION",
    "correlationId": "corr-retry-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `getRetryAmplification` | (global or scoped) | per-service retry metrics |
| 2 | `getOpenBreakers` | (global) | breaker states |
| 3 | `getCascadePath` | service | propagation graph |
| 4 (gated) | `applyBackpressure` / `tripBreaker` | target service | confirmation |

Steps 1-2 can execute in parallel.

---

## 5. LLM Node Configuration

### Root Cause Analyzer (Node 5)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a reliability engineer for an FX operations platform. Given: (1) retry amplification metrics, (2) open breaker states, (3) cascade propagation path — identify the ROOT cause service vs symptomatic services. Determine if this is a true storm (sustained > 5× baseline) or transient spike. If true storm: recommend backpressure point (rate-limit vs trip-breaker), affected flows, and recovery estimate. Output JSON: `{ stormConfirmed: bool, rootService, amplificationFactor, cascadePath[], affectedFlows[], recommendedAction, recoveryEstimateMin, confidence }`."
- **Temperature:** 0.1

---

## 6. HITL Gate

- **Placement:** After storm confirmation (Node 7)
- **Condition:** Storm confirmed with root identified
- **Wait node:** Resume on `POST /webhook/backpressure-approval/{executionId}`
- **Timeout:** 10 minutes (urgent during active storm)
- **Approval payload:**
  ```json
  {
    "executionId": "exec-retry-001",
    "decision": "APPROVED",
    "approverUserId": "user-sre-01",
    "action": "RATE_LIMIT",
    "targetService": "enrichment-service"
  }
  ```

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Metrics unavailable | Report "Unable to assess storm"; alert SRE |
| Cascade path unavailable | Identify root from metrics only |
| Backpressure call fails | Retry once; alert for manual intervention |
| Transient spike (not storm) | Report "Spike subsiding, no action" |
| Multiple roots | Identify and propose for primary root first |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| RETRY-EVAL-01 | Storm | 10× enrichment retries | Root + backpressure proposal |
| RETRY-EVAL-02 | HITL-approve | Approved | Calls applyBackpressure |
| RETRY-EVAL-03 | Cascade | 3 breakers open | Path traced to root |
| RETRY-EVAL-04 | Transient | Brief spike, stabilizing | "No action needed" |
| RETRY-EVAL-05 | HITL-deny | Denied | Logs, monitors |
| RETRY-EVAL-06 | Multi-root | Two root causes | Prioritized proposals |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Retry Detection | Nodes 2-3 (metrics + breakers) |
| Rq2: Cascade Path | Node 4 (graph trace) + Node 5 (LLM root ID) |
| Rq3: Backpressure + HITL | Nodes 6-9 (check + gate + apply) |
