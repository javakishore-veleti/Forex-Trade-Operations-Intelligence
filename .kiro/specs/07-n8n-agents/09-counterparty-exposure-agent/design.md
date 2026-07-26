# Design — Counterparty Exposure Narrative Agent

## 1. Overview

The Counterparty Exposure Agent produces live traceable narratives for
counterparty risk positions. It gathers exposure, limits, concentration,
collateral, and prior-day data in parallel and synthesizes a coherent story.
Risk L — read-only, no HITL gate.

**Trigger mechanism:** Webhook (Supervisor routing or on-demand/scheduled).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Parallel Data Fetch Group]
                           │
            ┌──────────────┼──────────────┬──────────────┬──────────────┐
            ▼              ▼              ▼              ▼              ▼
    [Get Exposure]   [Get Limits]   [Get Concentration] [Get Collateral] [Get Prior Day]
            │              │              │              │              │
            └──────────────┴──────────────┴──────────────┴──────────────┘
                                          │
                                          ▼
                              [LLM: Narrative Synthesizer]
                                          │
                                          ▼
                              [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ counterpartyId, question }` |
| 2 | Get Exposure | `n8n-nodes-base.httpRequest` | GET `getCounterpartyExposure(cp)` |
| 3 | Get Limits | `n8n-nodes-base.httpRequest` | GET `getLimits(cp)` |
| 4 | Get Concentration | `n8n-nodes-base.httpRequest` | GET `getConcentration(cp)` |
| 5 | Get Collateral | `n8n-nodes-base.httpRequest` | GET `getCollateral(cp)` |
| 6 | Get Prior Day | `n8n-nodes-base.httpRequest` | GET `getPriorDayExposure(cp)` |
| 7 | Narrative Synthesizer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: produce ExposureStory |
| 8 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns narrative |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/counterparty-exposure`
- **Method:** POST
- **Request body:**
  ```json
  {
    "counterpartyId": "FX-CP-001",
    "question": "What is the current exposure?",
    "correlationId": "corr-exp-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 (parallel) | `getCounterpartyExposure(cp)` | counterpartyId | gross/net exposure |
| 1 (parallel) | `getLimits(cp)` | counterpartyId | limit thresholds |
| 1 (parallel) | `getConcentration(cp)` | counterpartyId | pair/book/geo breakdown |
| 1 (parallel) | `getCollateral(cp)` | counterpartyId | collateral held |
| 1 (parallel) | `getPriorDayExposure(cp)` | counterpartyId | prior-day values |

All 5 calls execute in parallel.

---

## 5. LLM Node Configuration

### Narrative Synthesizer (Node 7)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a counterparty exposure narrator for an FX platform. Given exposure data, limits, concentration, collateral, and prior-day values — produce a coherent ExposureStory. Include: (1) current position summary, (2) limit utilization (flag >80%), (3) daily change with reason, (4) top-3 concentration risks, (5) collateral adequacy, (6) materiality assessment. Never invent numbers. If any data source failed, note the gap. Output as ExposureStory JSON envelope."
- **Temperature:** 0.2

---

## 6. HITL Gate

**Not applicable.** This agent is Risk L (read/explain only).

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Counterparty not found | Return "Counterparty {cp} not found" |
| One or more data sources timeout | Proceed with available data; note gaps |
| All data sources fail | Return "Unable to generate narrative — data unavailable" |
| LLM malformed output | Retry once; fallback to structured data summary |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| EXP-EVAL-01 | Integration | Query for FX-CP-001 | Full narrative |
| EXP-EVAL-02 | Limit | Near-breach counterparty | Urgency highlighted |
| EXP-EVAL-03 | Concentration | High concentration | Top-3 risks identified |
| EXP-EVAL-04 | Collateral | Low coverage | Flags insufficient |
| EXP-EVAL-05 | Degraded | One source down | Narrative with gap |
| EXP-EVAL-06 | Comparison | "vs yesterday" | Daily delta |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Live Narrative | Nodes 2-6 (data) + Node 7 (synthesis) |
| Rq2: Concentration | Node 4 (concentration data) + Node 7 |
| Rq3: Collateral | Node 5 (collateral data) + Node 7 |
| Rq4: Parallel Gathering | Nodes 2-6 execute concurrently |
