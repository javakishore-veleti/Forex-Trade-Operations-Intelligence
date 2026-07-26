# Design — Exception Materiality Agent

## 1. Overview

The Exception Materiality Agent classifies unresolved exceptions by materiality
(deterministic policy), explains classifications, and presents non-material
exceptions for bulk HITL approval before global close. Risk M.

**Trigger mechanism:** Webhook (pre-close trigger or on-demand).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Get Unresolved Exceptions] → [Loop: Per Exception]
                                                         │
                                                    [Get Exposure] → [Classify Materiality]
                                                         │
                                                   [End Loop]
                                                         │
                                                         ▼
                                         [LLM: Explain Classifications]
                                                         │
                                                         ▼
                                         [IF: Non-Material Exist?]
                                                  │           │
                                             yes           no
                                                  │           │
                                      [HITL: Bulk Approval]   │
                                            │         │       │
                                       approved   denied      │
                                            │         │       │
                                [Approve Batch]  [Hold]       │
                                            │         │       │
                                            └─────────┴───────┘
                                                         │
                                                         ▼
                                         [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ region, correlationId }` |
| 2 | Get Unresolved Exceptions | `n8n-nodes-base.httpRequest` | GET `getUnresolvedExceptions()` |
| 3 | Loop Exceptions | `n8n-nodes-base.splitInBatches` | Iterate per exception |
| 4 | Get Exposure | `n8n-nodes-base.httpRequest` | GET `getExposure(tradeId)` per exception |
| 5 | Classify Materiality | `n8n-nodes-base.httpRequest` | GET `classifyMateriality()` per exception |
| 6 | Explain Classifications (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: explain each classification |
| 7 | Non-Material Check (IF) | `n8n-nodes-base.if` | Branch if non-material exceptions exist |
| 8 | Bulk Approval (Wait) | `n8n-nodes-base.wait` | HITL gate for bulk approval |
| 9 | Approval Handler (IF) | `n8n-nodes-base.if` | Branch approved/denied |
| 10 | Approve Batch | `n8n-nodes-base.httpRequest` | POST `approveExceptionBatch(ids)` |
| 11 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns classification report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/exception-materiality`
- **Method:** POST
- **Request body:**
  ```json
  {
    "region": "GLOBAL",
    "cutoffTime": "2025-07-25T17:00:00Z",
    "correlationId": "corr-mat-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `getUnresolvedExceptions()` | region | exception list |
| 2 (per exception) | `getExposure(tradeId)` | tradeId | exposure/notional |
| 3 (per exception) | `classifyMateriality()` | exception + exposure | classification |
| 4 (gated) | `approveExceptionBatch(ids)` | exception IDs | approval confirmation |

---

## 5. LLM Node Configuration

### Classification Explainer (Node 6)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are an exception materiality analyst. Given exceptions with their materiality classifications and exposure data, explain each classification in business terms. For MATERIAL_BLOCKER: why it blocks (exposure, regulatory). For NON_MATERIAL: why it's tolerable. For REQUIRES_REVIEW: what's ambiguous. Rank material blockers by impact. Output as MaterialityReport with `blockers[]`, `nonMaterial[]`, `reviewRequired[]`."
- **Temperature:** 0.1

---

## 6. HITL Gate

- **Placement:** After classification explanation (Node 8)
- **Condition:** Non-material exceptions exist that need bulk approval
- **Wait node:** Resume on `POST /webhook/exception-approval/{executionId}`
- **Timeout:** 2 hours
- **Approval payload:**
  ```json
  {
    "executionId": "exec-mat-001",
    "decision": "APPROVED",
    "approverUserId": "user-eod-01",
    "exceptionIds": ["FX-EXC-001", "FX-EXC-002", "FX-EXC-003"]
  }
  ```

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Exception service unavailable | Report "unable to retrieve exceptions"; block close |
| Classification fails for one | Mark as REQUIRES_REVIEW; continue |
| Exposure not found | Use exception-stated notional; flag data gap |
| Approval timeout | Exceptions remain blocking |
| Batch approval fails | Report partial failure; identify which failed |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| MAT-EVAL-01 | Classification | 5 exceptions | 2 material, 3 non-material |
| MAT-EVAL-02 | Bulk | All non-material | Approval package presented |
| MAT-EVAL-03 | Material | High-exposure exception | Identified as blocker |
| MAT-EVAL-04 | HITL-approve | Approval granted | Batch marked approved |
| MAT-EVAL-05 | HITL-deny | Approval denied | Exceptions remain blocking |
| MAT-EVAL-06 | Empty | No exceptions | "No exceptions pending" |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Classification | Nodes 2-5 (fetch + classify loop) |
| Rq2: Explanation | Node 6 (LLM explainer) |
| Rq3: Approval Package | Nodes 7-10 (check + HITL + batch approve) |
