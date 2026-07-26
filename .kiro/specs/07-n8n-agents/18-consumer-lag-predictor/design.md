# Design — Consumer-Lag SLA Predictor Agent

## 1. Overview

The Consumer-Lag Predictor forecasts whether consumers will complete processing
before regional cutoffs. When a miss is predicted, it proposes human-gated
scaling or deferral. Risk H.

**Trigger mechanism:** Webhook (lag threshold breach from sidecar or scheduled check).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Get Lag by Partition] → [Get Completion Forecast] → [Get Hot Partition Keys]
                                                                                │
                                                                                ▼
                                                                [LLM: Scaling Advisor]
                                                                                │
                                                                                ▼
                                                                [IF: Cutoff Miss Predicted?]
                                                                      │            │
                                                                  yes           no
                                                                      │            │
                                                          [HITL: Scaling Gate]      │
                                                               │          │        │
                                                          approved    denied       │
                                                               │          │        │
                                                      [Scale Replicas]  [Defer]    │
                                                               │          │        │
                                                               └──────────┴────────┘
                                                                                │
                                                                                ▼
                                                                [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives lag threshold envelope |
| 2 | Get Lag by Partition | `n8n-nodes-base.httpRequest` | GET `getLagByPartition()` |
| 3 | Get Completion Forecast | `n8n-nodes-base.httpRequest` | GET `getCompletionForecast()` |
| 4 | Get Hot Partition Keys | `n8n-nodes-base.httpRequest` | GET `getHotPartitionKeys()` |
| 5 | Scaling Advisor (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: analyze + recommend |
| 6 | Cutoff Check (IF) | `n8n-nodes-base.if` | Branch if miss predicted |
| 7 | Scaling Gate (Wait) | `n8n-nodes-base.wait` | HITL for scale approval |
| 8 | Scale Handler (IF) | `n8n-nodes-base.if` | Branch approved/denied |
| 9 | Scale Replicas | `n8n-nodes-base.httpRequest` | POST `requestReplicaScale(from, to)` |
| 10 | Defer Processing | `n8n-nodes-base.httpRequest` | POST deferral notification |
| 11 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns forecast report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/consumer-lag-predictor`
- **Method:** POST
- **Request body:**
  ```json
  {
    "consumerGroup": "trade-risk-processor",
    "currentLag": 245000,
    "partitions": 18,
    "region": "EMEA",
    "cutoffDeadline": "2025-07-25T17:00:00Z",
    "correlationId": "corr-lag-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `getLagByPartition()` | consumerGroup | per-partition lag |
| 2 | `getCompletionForecast()` | lag + throughput | estimated completion |
| 3 | `getHotPartitionKeys()` | consumerGroup | skew keys |
| 4 (gated) | `requestReplicaScale(from, to)` | current, target | scale confirmation |

Steps 1-3 execute in parallel.

---

## 5. LLM Node Configuration

### Scaling Advisor (Node 5)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a Kafka consumer scaling advisor. Given: per-partition lag, completion forecast, cutoff deadline, and hot partition keys — determine: (1) Will processing finish before cutoff? (2) If not, is the cause overall throughput or partition skew? (3) If throughput: propose scale from X to Y replicas with estimated new completion time. (4) If skew: recommend rebalance. (5) Provide cost impact estimate. Output as ScalingProposal JSON with `willMiss`, `cause`, `proposal`, `estimatedCompletion`, `costImpact`."
- **Temperature:** 0.1

---

## 6. HITL Gate

- **Placement:** After scaling recommendation (Node 7)
- **Condition:** Cutoff miss predicted AND scaling proposed
- **Wait node:** Resume on `POST /webhook/lag-scaling-approval/{executionId}`
- **Timeout:** 30 minutes (urgent — near cutoff)
- **Approval payload:**
  ```json
  {
    "executionId": "exec-lag-001",
    "decision": "APPROVED",
    "approverUserId": "user-platform-01",
    "targetReplicas": 26
  }
  ```

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Kafka metrics unavailable | Report "unable to forecast"; alert ops |
| ML forecast fails | Use linear extrapolation fallback |
| Scale call fails | Retry once; fallback to alert-only |
| Cutoff already passed | Report "cutoff missed"; suggest post-cutoff reconciliation |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| LAG-EVAL-01 | Miss | Forecast exceeds cutoff | Scale proposal |
| LAG-EVAL-02 | On-track | Completion before cutoff | "On schedule" |
| LAG-EVAL-03 | Skew | Partition skew detected | Rebalance recommendation |
| LAG-EVAL-04 | HITL-approve | Scale approved | Calls requestReplicaScale |
| LAG-EVAL-05 | HITL-deny | Scale denied | Proposes deferral |
| LAG-EVAL-06 | Hot key | Single hot key | Reports key + partition |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Completion Forecasting | Nodes 2-3 (lag + forecast) |
| Rq2: Scaling Proposal + HITL | Nodes 5-9 (advisor + gate + scale) |
| Rq3: Hot Partition Detection | Node 4 (hot keys) + Node 5 (LLM analysis) |
