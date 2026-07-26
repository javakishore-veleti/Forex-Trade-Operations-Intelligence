# Design — Consumer-Lag SLA Predictor Agent

## 1. Overview

The Consumer-Lag SLA Predictor forecasts whether event consumers will complete
before regional cutoff deadlines. When at risk, it proposes scaling actions
via HITL gate. Risk H.

**Trigger mechanism:** Webhook (scheduled pre-cutoff check or lag threshold alert).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Get Lag By Partition] → [Get Completion Forecast]
                                                       │
                                                       ▼
                                          [Get Hot Partition Keys]
                                                       │
                                                       ▼
                                          [LLM: Scaling Planner]
                                                       │
                                                       ▼
                                          [IF: SLA At Risk?]
                                                 │          │
                                            yes          no
                                                 │          │
                                     [HITL: Scale Approval]  │
                                         │        │          │
                                    approved  denied         │
                                         │        │          │
                              [Request Scale]  [Hold]        │
                                         │        │          │
                                         └────────┴──────────┘
                                                       │
                                                       ▼
                                          [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ region, consumerGroup, correlationId }` |
| 2 | Get Lag By Partition | `n8n-nodes-base.httpRequest` | GET `getLagByPartition()` via MCP |
| 3 | Get Completion Forecast | `n8n-nodes-base.httpRequest` | GET `getCompletionForecast()` via Python sidecar |
| 4 | Get Hot Partition Keys | `n8n-nodes-base.httpRequest` | GET `getHotPartitionKeys()` via MCP |
| 5 | Scaling Planner (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: evaluate + propose |
| 6 | SLA At Risk Check (IF) | `n8n-nodes-base.if` | Branch if forecast > cutoff |
| 7 | Scale Approval Gate (Wait) | `n8n-nodes-base.wait` | HITL for scaling approval |
| 8 | Approval Handler (IF) | `n8n-nodes-base.if` | Branch approved/denied |
| 9 | Request Scale | `n8n-nodes-base.httpRequest` | POST `requestReplicaScale(from, to)` |
| 10 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns forecast + decision |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/consumer-lag-predictor`
- **Method:** POST
- **Request body:**
  ```json
  {
    "region": "EMEA",
    "consumerGroup": "trade-enrichment",
    "correlationId": "corr-lag-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `getLagByPartition()` | consumerGroup | per-partition lag offsets |
| 2 | `getCompletionForecast()` | lag data, region | minutes-to-drain |
| 3 | `getHotPartitionKeys()` | consumerGroup | keys causing skew |
| 4 (gated) | `requestReplicaScale(from, to)` | current, target | scale confirmation |

---

## 5. LLM Node Configuration

### Scaling Planner (Node 5)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a capacity planner for an FX operations platform. Given: (1) per-partition lag, (2) completion forecast vs cutoff, (3) hot partition keys — determine if SLA is at risk. If yes, calculate required replicas using: target_completion = forecast × (current_replicas / proposed_replicas). Consider partition skew impact. Produce ScalingProposal JSON with: `slaAtRisk`, `currentReplicas`, `proposedReplicas`, `currentForecastMin`, `afterScaleForecastMin`, `cutoffMin`, `hotPartitions[]`, `confidence`. Never exceed 2× current replicas without justification."
- **Temperature:** 0.1

---

## 6. HITL Gate

- **Placement:** After scaling proposal (Node 7)
- **Condition:** SLA_AT_RISK detected (forecast > cutoff)
- **Wait node:** Resume on `POST /webhook/lag-scale-approval/{executionId}`
- **Timeout:** 30 minutes (time-critical before cutoff)
- **Approval payload:**
  ```json
  {
    "executionId": "exec-lag-001",
    "decision": "APPROVED",
    "approverUserId": "user-infra-01",
    "proposedReplicas": 26
  }
  ```

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Lag metrics unavailable | Return "Unable to assess lag"; alert ops |
| Forecast model timeout | Use linear extrapolation fallback |
| Hot partition query fails | Proceed without skew analysis; note gap |
| Scale request fails | Retry once; fallback to alert notification |
| All partitions healthy | Return "On track for cutoff" |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| LAG-EVAL-01 | Integration | Lag exceeds cutoff | SLA_AT_RISK + proposal |
| LAG-EVAL-02 | HITL-approve | Scaling approved | Calls requestReplicaScale |
| LAG-EVAL-03 | HITL-deny | Scaling denied | Records decision, holds |
| LAG-EVAL-04 | Skew | Hot partition | Identifies key + impact |
| LAG-EVAL-05 | Healthy | Will complete in time | "On track" message |
| LAG-EVAL-06 | Multi-region | Multiple regions | Prioritized by proximity |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Completion Forecast | Nodes 2-3 (lag + forecast) |
| Rq2: Scaling Proposal + HITL | Nodes 5-9 (LLM + gate + scale) |
| Rq3: Partition Skew | Node 4 (hot keys) + Node 5 (LLM analysis) |
