# Design — Capacity & Backlog Planning Agent

## 1. Overview

The Capacity & Backlog Planning Agent forecasts backlog completion versus
regional deadlines and proposes scaling plans via HITL gate. Risk H.

**Trigger mechanism:** Webhook (scheduled backlog check or alert threshold).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Get Backlog] → [Get Completion Estimate]
                                              │
                                              ▼
                                [LLM: Capacity Planner]
                                              │
                                              ▼
                               [IF: Deadline At Risk?]
                                        │          │
                                   yes          no
                                        │          │
                            [Propose Scaling Plan]  │
                                        │          │
                            [HITL: Plan Approval]   │
                                │        │          │
                           approved  denied         │
                                │        │          │
                     [Apply Scaling]  [Hold]        │
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
| 2 | Get Backlog | `n8n-nodes-base.httpRequest` | GET `getBacklog(region)` |
| 3 | Get Completion Estimate | `n8n-nodes-base.httpRequest` | GET `getCompletionEstimate()` |
| 4 | Capacity Planner (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: analyze + recommend |
| 5 | Deadline Check (IF) | `n8n-nodes-base.if` | Branch if estimate > deadline |
| 6 | Propose Scaling Plan | `n8n-nodes-base.httpRequest` | POST `proposeScalingPlan()` |
| 7 | Plan Approval Gate (Wait) | `n8n-nodes-base.wait` | HITL for scaling approval |
| 8 | Approval Handler (IF) | `n8n-nodes-base.if` | Branch approved/denied |
| 9 | Apply Scaling Plan | `n8n-nodes-base.httpRequest` | POST `applyScalingPlan()` |
| 10 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns capacity report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/capacity-backlog`
- **Method:** POST
- **Request body:**
  ```json
  {
    "region": "EMEA",
    "correlationId": "corr-cap-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `getBacklog` | region | current counts |
| 2 | `getCompletionEstimate` | backlog, throughput | minutes to clear |
| 3 | `proposeScalingPlan` | estimate, deadline | scaling proposal |
| 4 (gated) | `applyScalingPlan` | approved plan | scaling confirmation |

---

## 5. LLM Node Configuration

### Capacity Planner (Node 4)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a capacity planner for an FX trade operations platform. Given: (1) current backlog per region, (2) completion estimate vs deadline — determine if deadline is at risk. If yes, identify bottleneck type (CPU, I/O, downstream, partition-skew) and the most effective scaling lever. Consider alternatives: scale-up, defer non-critical, re-prioritize. Produce JSON: `{ deadlineAtRisk: bool, currentBacklog, estimateMin, deadlineMin, bottleneck, alternatives[], recommendedAction, expectedImprovement }`. Never recommend more than 3× current capacity."
- **Temperature:** 0.1

---

## 6. HITL Gate

- **Placement:** After scaling plan proposal (Node 7)
- **Condition:** Deadline at risk
- **Wait node:** Resume on `POST /webhook/capacity-approval/{executionId}`
- **Timeout:** 20 minutes (deadline-sensitive)
- **Approval payload:**
  ```json
  {
    "executionId": "exec-cap-001",
    "decision": "APPROVED",
    "approverUserId": "user-infra-01",
    "planId": "plan-emea-scale-001"
  }
  ```

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Backlog service unavailable | Return "Unable to assess"; alert ops |
| Completion model timeout | Use linear extrapolation fallback |
| Scaling plan generation fails | Report deadline risk without plan |
| Apply fails | Retry once; alert ops manually |
| On track | Return "Backlog on track for deadline" |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| CAP-EVAL-01 | At risk | 47min vs 31min deadline | Scaling plan proposed |
| CAP-EVAL-02 | HITL-approve | Plan approved | Applies scaling |
| CAP-EVAL-03 | HITL-deny | Plan denied | Suggests alternatives |
| CAP-EVAL-04 | Bottleneck | DB throughput limited | Identifies DB constraint |
| CAP-EVAL-05 | Healthy | Will clear on time | "On track" |
| CAP-EVAL-06 | Multi-region | APAC+EMEA at risk | Prioritized list |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Backlog Assessment | Nodes 2-3 (backlog + estimate) |
| Rq2: Scaling Plan + HITL | Nodes 5-9 (check + propose + gate + apply) |
| Rq3: Multi-Factor Analysis | Node 4 (LLM bottleneck analysis) |
