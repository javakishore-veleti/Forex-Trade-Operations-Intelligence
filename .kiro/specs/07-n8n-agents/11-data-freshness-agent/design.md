# Design — Data Freshness & Decision-Suitability Agent

## 1. Overview

The Data Freshness Agent gates critical processes by verifying input data
freshness, completeness, and authoritativeness. The BLOCK/ACCEPT verdict
is deterministic (policy service); the agent explains impact and manages
override approvals. Risk M.

**Trigger mechanism:** Webhook (pre-process gate invoked by orchestration).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Fetch Freshness] → [Fetch Completeness] → [Fetch Authoritativeness]
                                                                         │
                                                                         ▼
                                                         [Get Suitability Verdict]
                                                                         │
                                                                         ▼
                                                         [IF: Verdict = BLOCK?]
                                                              │            │
                                                          BLOCK         ACCEPT
                                                              │            │
                                                   [LLM: Impact Explainer] │
                                                              │            │
                                                   [HITL: Override Gate]    │
                                                        │          │       │
                                                  approved    denied       │
                                                        │          │       │
                                               [Record Exception] [Hold]   │
                                                        │          │       │
                                                        └──────────┴───────┘
                                                                         │
                                                                         ▼
                                                         [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ datasets[], process, region }` |
| 2 | Fetch Freshness | `n8n-nodes-base.httpRequest` | GET `getDatasetFreshness(ds)` per dataset |
| 3 | Fetch Completeness | `n8n-nodes-base.httpRequest` | GET `getCompleteness(ds)` per dataset |
| 4 | Fetch Authoritativeness | `n8n-nodes-base.httpRequest` | GET `getAuthoritativeness(ds)` per dataset |
| 5 | Get Verdict | `n8n-nodes-base.httpRequest` | GET `getSuitabilityVerdict(ds, process)` |
| 6 | Verdict Check (IF) | `n8n-nodes-base.if` | Branch on BLOCK/ACCEPT |
| 7 | Impact Explainer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: explain why blocked |
| 8 | Override Gate (Wait) | `n8n-nodes-base.wait` | Wait for human override decision |
| 9 | Override Handler (IF) | `n8n-nodes-base.if` | Branch approved/denied |
| 10 | Record Exception | `n8n-nodes-base.httpRequest` | POST exception to audit store |
| 11 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns suitability report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/data-freshness`
- **Method:** POST
- **Request body:**
  ```json
  {
    "datasets": ["market-rates-emea", "trade-positions-emea"],
    "process": "EOD_RISK_AGGREGATION",
    "region": "EMEA",
    "correlationId": "corr-fresh-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 (parallel per ds) | `getDatasetFreshness(ds)` | dataset ID | timestamp, staleness |
| 2 (parallel per ds) | `getCompleteness(ds)` | dataset ID | row count, coverage % |
| 3 (parallel per ds) | `getAuthoritativeness(ds)` | dataset ID | source authority status |
| 4 | `getSuitabilityVerdict(ds, process)` | dataset + process | BLOCK/ACCEPT |

---

## 5. LLM Node Configuration

### Impact Explainer (Node 7)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "Explain why the data freshness gate is blocking this process. Include: (1) which dataset(s) failed, (2) how stale (actual vs max), (3) downstream impact if proceeded, (4) recommended action. Present concisely for a human approver to make an override decision. Never recommend auto-override."
- **Temperature:** 0.1

---

## 6. HITL Gate

- **Placement:** After Impact Explainer (Node 8)
- **Condition:** Verdict = BLOCK
- **Wait node:** Resume on `POST /webhook/freshness-override/{executionId}`
- **Timeout:** 1 hour
- **Approval payload:**
  ```json
  {
    "executionId": "exec-fresh-001",
    "decision": "APPROVED",
    "approverUserId": "user-eod-01",
    "approvalReference": "apr-fresh-001",
    "justification": "Market data provider confirmed delay is cosmetic"
  }
  ```

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Data catalog unavailable | Default to BLOCK (fail-safe) |
| Policy service timeout | Default to BLOCK; notify operator |
| Dataset not found | Report "Unknown dataset"; BLOCK |
| Override timeout | Process remains blocked; alert operator |
| LLM failure | Present raw verdict data without explanation |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| FRESH-EVAL-01 | All fresh | Fresh datasets | ACCEPT response |
| FRESH-EVAL-02 | Stale | 45 min stale (max 15) | BLOCK + override gate |
| FRESH-EVAL-03 | Override-approve | Override approved | Exception recorded; ACCEPT |
| FRESH-EVAL-04 | Incomplete | Low completeness | BLOCK |
| FRESH-EVAL-05 | Wrong source | Non-authoritative | BLOCK |
| FRESH-EVAL-06 | Override-deny | Override denied | BLOCK maintained |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Freshness Gate | Nodes 2 (freshness) + 5 (verdict) + 6 (check) |
| Rq2: Completeness/Authority | Nodes 3 + 4 (completeness, authority) |
| Rq3: HITL Override | Nodes 7-10 (explain + gate + record) |
