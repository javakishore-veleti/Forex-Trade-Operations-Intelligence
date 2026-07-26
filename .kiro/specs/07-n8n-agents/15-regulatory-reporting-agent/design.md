# Design — Regulatory Reporting Completeness Agent

## 1. Overview

The Regulatory Reporting Agent verifies reporting completeness by diffing the
reportable universe against submissions, explains gaps, and gates resubmission.
Risk M.

**Trigger mechanism:** Webhook (pre-deadline sweep or on-demand).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Get Reportable Universe] → [Get Submitted Reports] → [Find Gaps]
                                                                                │
                                                                                ▼
                                                                [Get Validation Failures]
                                                                                │
                                                                                ▼
                                                                [LLM: Gap Explainer]
                                                                                │
                                                                                ▼
                                                                [IF: Gaps Exist?]
                                                                     │          │
                                                                 yes         no
                                                                     │          │
                                                         [HITL: Resubmit Gate]  │
                                                              │         │       │
                                                         approved   denied      │
                                                              │         │       │
                                                     [Resubmit Report] [Log]    │
                                                              │         │       │
                                                              └─────────┴───────┘
                                                                                │
                                                                                ▼
                                                                [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ regime, deadline }` |
| 2 | Get Reportable Universe | `n8n-nodes-base.httpRequest` | GET `getReportableUniverse()` |
| 3 | Get Submitted Reports | `n8n-nodes-base.httpRequest` | GET `getSubmittedReports()` |
| 4 | Find Gaps | `n8n-nodes-base.httpRequest` | GET `findReportingGaps()` |
| 5 | Get Validation Failures | `n8n-nodes-base.httpRequest` | GET `getFieldValidationFailures()` |
| 6 | Gap Explainer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: explain gaps + patterns |
| 7 | Gaps Check (IF) | `n8n-nodes-base.if` | Branch if correctable gaps exist |
| 8 | Resubmit Gate (Wait) | `n8n-nodes-base.wait` | HITL for resubmission approval |
| 9 | Approval Handler (IF) | `n8n-nodes-base.if` | Branch approved/denied |
| 10 | Resubmit Report | `n8n-nodes-base.httpRequest` | POST `resubmitReport()` |
| 11 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns completeness report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/regulatory-reporting`
- **Method:** POST
- **Request body:**
  ```json
  {
    "regime": "FX-REG-SYNTH-01",
    "deadline": "2025-07-25T18:00:00Z",
    "region": "EMEA",
    "correlationId": "corr-reg-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `getReportableUniverse()` | regime | reportable trade set |
| 2 | `getSubmittedReports()` | regime | submitted set |
| 3 | `findReportingGaps()` | regime | gap list |
| 4 | `getFieldValidationFailures()` | regime | validation errors |
| 5 (gated) | `resubmitReport()` | tradeId, payload | resubmission confirmation |

Steps 1-2 execute in parallel.

---

## 5. LLM Node Configuration

### Gap Explainer (Node 6)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a regulatory reporting analyst. Given: reportable universe, submitted reports, gaps, and validation failures — explain each gap (late capture, validation failure, system error, exclusion). Identify systemic patterns (branch-level, feed-level). Categorize by priority (deadline proximity, materiality). Output as CompletenessReport JSON with `completionPct`, `gaps[]`, `patterns[]`, `validationIssues[]`."
- **Temperature:** 0.1

---

## 6. HITL Gate

- **Placement:** After gap explanation (Node 8)
- **Condition:** Correctable gaps identified
- **Wait node:** Resume on `POST /webhook/reporting-resubmit/{executionId}`
- **Timeout:** 2 hours (before deadline)

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Reporting service unavailable | Report "unable to verify completeness"; alert compliance |
| Universe empty (unexpected) | Flag as anomaly; do not attest |
| Resubmission fails | Report failure; suggest manual resubmission |
| Gap diff timeout | Return partial results with warning |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| REG-EVAL-01 | Integration | Pre-deadline sweep | Identifies gaps |
| REG-EVAL-02 | Complete | All reported | Attestation ready |
| REG-EVAL-03 | Pattern | Systemic branch gap | Pattern identified |
| REG-EVAL-04 | HITL-approve | Resubmission approved | Calls resubmitReport |
| REG-EVAL-05 | HITL-deny | Resubmission denied | Gap documented |
| REG-EVAL-06 | Validation | Field errors | Lists with suggestions |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Completeness Verification | Nodes 2-4 (universe + submitted + diff) |
| Rq2: Gap Explanation | Nodes 5-6 (validation + LLM) |
| Rq3: Resubmission Gate | Nodes 7-10 (check + HITL + resubmit) |
