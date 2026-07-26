# Design — Business KPI Guard Agent

## 1. Overview

The Business KPI Guard detects anomalies in business metrics (booking rate,
rejection rate, throughput) via Python sidecar trigger, then investigates root
cause using deterministic data and LLM reasoning. Risk L — advisory only.

**Trigger mechanism:** Webhook (AnomalyEnvelope from kpi-anomaly-detector sidecar).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Get Business KPIs] → [Get Seasonal Baseline] → [Get Reject Breakdown]
                                                                            │
                                                                            ▼
                                                            [LLM: Root Cause Analyzer]
                                                                            │
                                                                            ▼
                                                            [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives AnomalyEnvelope from sidecar |
| 2 | Get Business KPIs | `n8n-nodes-base.httpRequest` | GET `getBusinessKpis(region)` |
| 3 | Get Seasonal Baseline | `n8n-nodes-base.httpRequest` | GET `getSeasonalBaseline(kpi)` |
| 4 | Get Reject Breakdown | `n8n-nodes-base.httpRequest` | GET `getRejectBreakdown()` |
| 5 | Root Cause Analyzer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: correlate + explain |
| 6 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns anomaly report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/business-kpi-guard`
- **Method:** POST
- **Request body:**
  ```json
  {
    "kpi": "booking_rate",
    "region": "APAC",
    "currentValue": 142,
    "baselineValue": 241,
    "deviationPct": -41.1,
    "detectedAt": "2025-07-25T09:15:00Z",
    "calendarContext": { "holiday": false, "earlyClose": false },
    "correlationId": "corr-kpi-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `getBusinessKpis(region)` | region | all current KPIs |
| 2 | `getSeasonalBaseline(kpi)` | kpi name | expected value + bounds |
| 3 | `getRejectBreakdown()` | region | rejection reasons |

All 3 calls execute in parallel.

---

## 5. LLM Node Configuration

### Root Cause Analyzer (Node 5)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a business KPI analyst for an FX platform. Given: anomaly envelope, current KPIs, seasonal baseline, reject breakdown, and calendar context — determine: (1) Is this a real anomaly or calendar-driven? (2) What is the affected scope (region/book/pair)? (3) What is the probable root cause? (4) Should Change Correlation Agent be invoked? Never invent data. Output as KPIAnomalyReport JSON with `confirmed`, `scope`, `probableCause`, `suggestedAction`."
- **Temperature:** 0.1

---

## 6. HITL Gate

**Not applicable.** This agent is Risk L (advisory only).

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| KPI service unavailable | Report "unable to verify"; relay sidecar data only |
| Baseline not found | Use sidecar's baseline from envelope; note source |
| Reject breakdown empty | Skip rejection analysis; focus on volume |
| LLM malformed output | Retry once; return raw anomaly data |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| KPI-EVAL-01 | Anomaly | APAC booking 41% below | Root cause explanation |
| KPI-EVAL-02 | Calendar | Holiday dip | "Calendar-driven, suppressed" |
| KPI-EVAL-03 | Rejection | EUR rejection spike | Correlates with rule change |
| KPI-EVAL-04 | Normal | KPIs within bounds | Not triggered |
| KPI-EVAL-05 | On-demand | Manual query | Retrieves + explains |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Anomaly-Triggered | Node 1 (webhook from sidecar) + Nodes 2-3 (verify) |
| Rq2: Root Cause Correlation | Node 4 (rejects) + Node 5 (LLM analysis) |
| Rq3: Calendar-Aware | Calendar context in trigger + LLM prompt |
