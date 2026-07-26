# Design — FinOps Cost-Anomaly Agent

## 1. Overview

The FinOps Cost-Anomaly Agent detects cost spikes, correlates them to
deployments/volume changes, and proposes rightsizing actions via HITL gate.
Risk H.

**Trigger mechanism:** Webhook (from cost anomaly alert or scheduled check).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Get Cost By Service] → [Correlate Cost to Deploy]
                                                       │
                                                       ▼
                                          [Get Idle Capacity]
                                                       │
                                                       ▼
                                          [LLM: Cost Analyzer]
                                                       │
                                                       ▼
                                          [IF: Action Needed?]
                                                  │          │
                                             yes          no
                                                  │          │
                                      [Propose Rightsizing]   │
                                                  │          │
                                      [HITL: Scale Approval]  │
                                          │        │         │
                                     approved  denied        │
                                          │        │         │
                               [Apply Scale Down]  [Hold]    │
                                          │        │         │
                                          └────────┴─────────┘
                                                       │
                                                       ▼
                                          [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ timeRange, correlationId }` |
| 2 | Get Cost By Service | `n8n-nodes-base.httpRequest` | GET `getCostByService()` |
| 3 | Correlate Cost to Deploy | `n8n-nodes-base.httpRequest` | GET `correlateCostToDeploy()` |
| 4 | Get Idle Capacity | `n8n-nodes-base.httpRequest` | GET `getIdleCapacity()` |
| 5 | Cost Analyzer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: root cause + plan |
| 6 | Action Check (IF) | `n8n-nodes-base.if` | Branch if action warranted |
| 7 | Propose Rightsizing | `n8n-nodes-base.httpRequest` | POST `proposeRightsizing()` |
| 8 | Scale Approval Gate (Wait) | `n8n-nodes-base.wait` | HITL for scale-down |
| 9 | Approval Handler (IF) | `n8n-nodes-base.if` | Branch approved/denied |
| 10 | Apply Scale Down | `n8n-nodes-base.httpRequest` | POST `applyScaleDown()` |
| 11 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns cost report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/finops-cost`
- **Method:** POST
- **Request body:**
  ```json
  {
    "timeRange": "24h",
    "threshold": 0.3,
    "correlationId": "corr-finops-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `getCostByService` | timeRange | per-service costs |
| 2 | `correlateCostToDeploy` | anomalies | deploy correlations |
| 3 | `getIdleCapacity` | region/service | idle resources |
| 4 | `proposeRightsizing` | idle + anomalies | proposals |
| 5 (gated) | `applyScaleDown` | approved plan | confirmation |

Steps 2-3 can execute in parallel.

---

## 5. LLM Node Configuration

### Cost Analyzer (Node 5)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a FinOps analyst for an FX trade operations platform. Given: (1) per-service cost data, (2) cost-to-deploy correlations, (3) idle capacity — determine if cost anomalies are deployment-driven (unexpected) or volume-driven (expected). For unexpected: identify the deployment and explain the causal mechanism. For idle: recommend rightsizing with SLA risk assessment. Output JSON: `{ anomalies[], correlations[], volumeDriven[], idleResources[], recommendations[], estimatedSavings, summary }`."
- **Temperature:** 0.1

---

## 6. HITL Gate

- **Placement:** After rightsizing proposal (Node 8)
- **Condition:** Actionable savings identified
- **Wait node:** Resume on `POST /webhook/finops-approval/{executionId}`
- **Timeout:** 24 hours (non-urgent cost optimization)
- **Approval payload:**
  ```json
  {
    "executionId": "exec-finops-001",
    "decision": "APPROVED",
    "approverUserId": "user-finops-01",
    "proposalId": "prop-rightsize-001"
  }
  ```

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Cost data unavailable | Return "Unable to assess costs" |
| Deploy history unavailable | Report anomaly without correlation |
| Rightsizing computation fails | Report anomalies without action |
| Scale-down fails | Retry once; alert ops |
| No anomalies | Return "Costs within normal bounds" |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| FINOPS-EVAL-01 | Anomaly | Risk-engine +45% | Correlated to deploy |
| FINOPS-EVAL-02 | HITL-approve | Scale-down approved | Calls applyScaleDown |
| FINOPS-EVAL-03 | Idle | Weekend low-volume | Rightsizing proposed |
| FINOPS-EVAL-04 | HITL-deny | Denied | Logs, monitors |
| FINOPS-EVAL-05 | Expected | Volume-driven increase | Flagged EXPECTED |
| FINOPS-EVAL-06 | Normal | Costs within baseline | "No anomalies" |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Cost Anomaly Detection | Nodes 2 (costs) + Node 5 (LLM) |
| Rq2: Deploy Correlation | Node 3 (correlation) + Node 5 (LLM) |
| Rq3: Rightsizing + HITL | Nodes 4, 6-10 (idle + check + propose + gate + apply) |
