# Design — Databricks Lineage & Freshness Impact Agent

## 1. Overview

The Lineage Agent traces downstream impact of analytics pipeline failures,
verifies aggregation readiness, and gates downstream processes. Risk M.

**Trigger mechanism:** Webhook (pipeline failure event or pre-aggregation check).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Get Job Status] → [Get Lineage Downstream] → [Get Aggregation Readiness]
                                                                            │
                                                                            ▼
                                                            [LLM: Impact Explainer]
                                                                            │
                                                                            ▼
                                                            [IF: Block Required?]
                                                                 │           │
                                                             yes          no
                                                                 │           │
                                                     [HITL: Block Gate]      │
                                                          │        │         │
                                                     approved  denied        │
                                                          │        │         │
                                             [Block Aggregation]  [Pass]     │
                                                          │        │         │
                                                          └────────┴─────────┘
                                                                            │
                                                                            ▼
                                                            [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ table, jobId, eventType }` |
| 2 | Get Job Status | `n8n-nodes-base.httpRequest` | GET `getJobStatus()` |
| 3 | Get Lineage Downstream | `n8n-nodes-base.httpRequest` | GET `getLineageDownstream(table)` |
| 4 | Get Aggregation Readiness | `n8n-nodes-base.httpRequest` | GET `getAggregationReadiness(region)` |
| 5 | Impact Explainer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: explain impact chain |
| 6 | Block Check (IF) | `n8n-nodes-base.if` | Branch if block needed |
| 7 | Block Gate (Wait) | `n8n-nodes-base.wait` | HITL for block approval |
| 8 | Block Handler (IF) | `n8n-nodes-base.if` | Branch approved/denied |
| 9 | Block Aggregation | `n8n-nodes-base.httpRequest` | POST `blockAggregation()` |
| 10 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns impact report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/databricks-lineage`
- **Method:** POST
- **Request body:**
  ```json
  {
    "table": "fx_rates_daily",
    "jobId": "fx-rates-etl-001",
    "eventType": "JOB_FAILURE",
    "region": "EMEA",
    "correlationId": "corr-lin-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `getJobStatus()` | jobId | completion status |
| 2 | `getLineageDownstream(table)` | table | dependency graph |
| 3 | `getAggregationReadiness(region)` | region | readiness status |
| 4 (gated) | `blockAggregation()` | region | block confirmation |

---

## 5. LLM Node Configuration

### Impact Explainer (Node 5)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a data lineage analyst for an FX platform. Given job status, downstream lineage graph, and aggregation readiness — explain the impact chain: source failure → intermediate tables → final reports/calcs. Include: affected regions, criticality, time-to-impact, and suggested remediation. Output as LineageImpactReport JSON."
- **Temperature:** 0.1

---

## 6. HITL Gate

- **Placement:** After impact explanation (Node 7)
- **Condition:** Aggregation readiness is INCOMPLETE and impact is material
- **Wait node:** Resume on `POST /webhook/lineage-block-approval/{executionId}`
- **Timeout:** 1 hour

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Lineage service unavailable | Report "lineage unknown"; recommend manual check |
| Job status unknown | Treat as potentially failed; flag |
| Block call fails | Retry once; alert operator |
| Table not in catalog | Return "Table not found in lineage" |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| LIN-EVAL-01 | Failure | Job fails | Impact chain to EMEA aggregation |
| LIN-EVAL-02 | Healthy | All jobs OK | "All inputs ready" |
| LIN-EVAL-03 | Schema | Schema change | Consumer impact list |
| LIN-EVAL-04 | HITL-approve | Block approved | Calls blockAggregation |
| LIN-EVAL-05 | HITL-deny | Block denied | Aggregation proceeds |
| LIN-EVAL-06 | Multiple | Multiple failures | Prioritized list |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Lineage Tracing | Nodes 2-3 (job status + lineage) |
| Rq2: Aggregation Gate | Nodes 4 + 6-9 (readiness + HITL + block) |
| Rq3: Impact Explanation | Node 5 (LLM explainer) |
