# Design — Adaptive Transaction Routing Agent

## 1. Overview

The Adaptive Transaction Routing Agent proposes temporary routing policy
changes when service degradation or capacity constraints are detected.
Policies are validated by deterministic rules before HITL approval. Risk H.

**Trigger mechanism:** Webhook (from observability alert or scheduled condition check).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Get Runtime Conditions] → [LLM: Condition Analyzer]
                                                        │
                                                        ▼
                                          [Propose Routing Policy]
                                                        │
                                                        ▼
                                          [Validate Routing Policy]
                                                        │
                                                        ▼
                                          [IF: Valid + Action Needed?]
                                                   │          │
                                              yes          no
                                                   │          │
                                       [HITL: Policy Approval]│
                                           │        │         │
                                      approved  denied        │
                                           │        │         │
                                [Apply Routing]  [Hold]       │
                                           │        │         │
                                           └────────┴─────────┘
                                                        │
                                                        ▼
                                          [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ region, trigger, correlationId }` |
| 2 | Get Runtime Conditions | `n8n-nodes-base.httpRequest` | GET `getRuntimeConditions()` |
| 3 | Condition Analyzer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: analyze conditions |
| 4 | Propose Routing Policy | `n8n-nodes-base.httpRequest` | POST `proposeRoutingPolicy()` |
| 5 | Validate Routing Policy | `n8n-nodes-base.httpRequest` | POST `validateRoutingPolicy()` |
| 6 | Action Check (IF) | `n8n-nodes-base.if` | Branch if valid + needed |
| 7 | Policy Approval Gate (Wait) | `n8n-nodes-base.wait` | HITL for policy approval |
| 8 | Approval Handler (IF) | `n8n-nodes-base.if` | Branch approved/denied |
| 9 | Apply Routing Config | `n8n-nodes-base.httpRequest` | POST `applyRoutingConfig()` |
| 10 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns routing decision |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/adaptive-routing`
- **Method:** POST
- **Request body:**
  ```json
  {
    "region": "EMEA",
    "trigger": "SERVICE_DEGRADATION",
    "correlationId": "corr-route-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `getRuntimeConditions` | region | health, capacity, cutoffs |
| 2 | `proposeRoutingPolicy` | conditions | candidate policy |
| 3 | `validateRoutingPolicy` | policy | validation result |
| 4 (gated) | `applyRoutingConfig` | validated policy | application result |

---

## 5. LLM Node Configuration

### Condition Analyzer (Node 3)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a transaction routing analyst for an FX operations platform. Given runtime conditions (service health, capacity, cutoff proximity), determine if routing adjustment is warranted. Consider: current error rates, latency vs SLA, queue depths, cutoff time pressure. Produce JSON: `{ adjustmentNeeded: bool, rationale, affectedFlows[], suggestedStrategy, temporaryDuration }`. Do not suggest routing changes that orphan trades."
- **Temperature:** 0.1

---

## 6. HITL Gate

- **Placement:** After policy validation (Node 7)
- **Condition:** Valid policy + routing change needed
- **Wait node:** Resume on `POST /webhook/routing-approval/{executionId}`
- **Timeout:** 15 minutes (time-critical)
- **Approval payload:**
  ```json
  {
    "executionId": "exec-route-001",
    "decision": "APPROVED",
    "approverUserId": "user-infra-01",
    "policyId": "pol-route-emea-001"
  }
  ```

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Conditions unavailable | Return "Unable to assess routing" |
| Policy validation rejects | Revise once with LLM; if still invalid, report only |
| Apply fails | Retry once; alert ops manually |
| All services healthy | Return "No routing adjustment needed" |
| LLM proposes invalid strategy | Validation catches; no application |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| ROUTE-EVAL-01 | Degradation | enrichment-svc slow | Reroute proposed |
| ROUTE-EVAL-02 | HITL-approve | Policy approved | Applies config |
| ROUTE-EVAL-03 | Invalid | Orphans trades | Validation rejects |
| ROUTE-EVAL-04 | Multi-service | Two services degraded | Composite policy |
| ROUTE-EVAL-05 | Healthy | All within SLA | "No adjustment" |
| ROUTE-EVAL-06 | HITL-deny | Policy denied | Holds; logged |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Condition Assessment | Nodes 2-3 (conditions + LLM) |
| Rq2: Policy Proposal | Nodes 4-5 (propose + validate) |
| Rq3: Application + HITL | Nodes 6-9 (check + gate + apply) |
