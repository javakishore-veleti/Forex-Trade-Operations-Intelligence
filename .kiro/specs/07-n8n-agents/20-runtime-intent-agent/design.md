# Design — Runtime Intent-Inference Agent

## 1. Overview

The Runtime Intent-Inference Agent classifies system activity bursts into
operational intents (EOD ramp, failover, incident, etc.) and provides
suppression guidance to reduce false alarms. Risk L — read/classify only.

**Trigger mechanism:** Webhook (from Supervisor Agent or scheduled probe).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Fetch Recent Activity] → [Behavioral Clustering]
                                                        │
                                                        ▼
                                          [Align to Business Goal]
                                                        │
                                                        ▼
                                          [LLM: Intent Classifier]
                                                        │
                                                        ▼
                                          [Build Intent Envelope]
                                                        │
                                                        ▼
                                          [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ window, region, correlationId }` |
| 2 | Fetch Recent Activity | `n8n-nodes-base.httpRequest` | GET `getRecentActivity(window)` via MCP |
| 3 | Behavioral Clustering | `n8n-nodes-base.httpRequest` | POST `classifyIntent()` — Python sidecar |
| 4 | Align to Business Goal | `n8n-nodes-base.httpRequest` | GET `alignToBusinessGoal()` via calendar-mcp |
| 5 | Intent Classifier (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: classify intent + suppression |
| 6 | Build Intent Envelope | `n8n-nodes-base.set` | Structure final IntentEnvelope |
| 7 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns IntentEnvelope |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/runtime-intent`
- **Method:** POST
- **Request body:**
  ```json
  {
    "window": "15m",
    "region": "APAC",
    "correlationId": "corr-intent-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Endpoint | Input | Output |
|------|------|----------|-------|--------|
| 1 | `getRecentActivity` | observability-mcp | window, region | metrics, events, deploys |
| 2 | `classifyIntent` | observability-mcp | activity signals | cluster labels + centroids |
| 3 | `alignToBusinessGoal` | calendar-mcp | window, region | matching goals or empty |

Steps 2 and 3 depend on step 1; steps 2-3 can run in parallel.

---

## 5. LLM Node Configuration

### Intent Classifier (Node 5)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are an operational intent classifier for an FX trade platform. Given: (1) clustered activity signals, (2) business goal alignment data — classify the activity into one of: EOD_RAMP, FAILOVER, LOAD_TEST, INCIDENT, MAINTENANCE, UNKNOWN. Provide confidence (0-1), evidence list, and suppression guidance (scope, duration). If confidence > 0.8 and intent aligns with a goal, set suppressAlerts=true. Never suppress for INCIDENT or UNKNOWN. Output as IntentEnvelope JSON."
- **Temperature:** 0.1

---

## 6. HITL Gate

**Not applicable.** This agent is Risk L (classification only). No actions are proposed or executed.

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Observability service timeout | Return partial classification with caveat |
| Clustering sidecar unavailable | Skip clustering; LLM classifies from raw signals |
| Calendar service down | Classify without goal alignment; suppressAlerts=false |
| No activity in window | Return "No significant activity burst detected" |
| LLM malformed output | Retry once; fallback to UNKNOWN with confidence=0 |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| INTENT-EVAL-01 | Integration | APAC EOD window burst | EOD_RAMP, suppress=true |
| INTENT-EVAL-02 | Alignment | Failover drill + calendar | FAILOVER, aligned=true |
| INTENT-EVAL-03 | Unknown | Anomalous burst, no schedule | UNKNOWN, suppress=false |
| INTENT-EVAL-04 | Calendar match | Load test + entry | LOAD_TEST |
| INTENT-EVAL-05 | Low confidence | Mixed signals | confidence<0.8, suppress=false |
| INTENT-EVAL-06 | Quiet | No burst | "No significant activity" |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Activity Classification | Nodes 2-3 (fetch + cluster) + Node 5 (LLM) |
| Rq2: Business Goal Alignment | Node 4 (calendar) + Node 5 (LLM) |
| Rq3: Suppression Guidance | Node 5 (LLM) + Node 6 (envelope build) |
