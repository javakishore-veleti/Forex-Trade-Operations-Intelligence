# Design — End-of-Day Risk Readiness Agent

## 1. Overview

The EOD Readiness Agent is a multi-agent workflow with regional sub-agents
(APAC, EMEA, AMER) and a global supervisor. It assesses readiness, identifies
blockers, supports exception approval, and gates global consolidation start.
Risk M.

**Trigger mechanism:** Webhook (scheduled during EOD window or on-demand).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Spawn Regional Sub-Agents (Parallel)]
                           │
            ┌──────────────┼──────────────┐
            ▼              ▼              ▼
    [APAC Sub-Agent] [EMEA Sub-Agent] [AMER Sub-Agent]
            │              │              │
            └──────────────┴──────────────┘
                           │
                           ▼
            [LLM: Global Readiness Synthesizer]
                           │
                           ▼
            [IF: Blockers Exist?]
                  │              │
            has blockers     all ready
                  │              │
            [HITL: Exception]   │
                  │              │
            [Re-evaluate]       │
                  │              │
                  └──────────────┘
                           │
                           ▼
            [HITL: Consolidation Approval]
                  │              │
            approved         denied
                  │              │
    [Start Consolidation]  [Hold]
                  │              │
                  └──────────────┘
                           │
                           ▼
            [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives EOD readiness request |
| 2 | APAC Sub-Agent | `n8n-nodes-base.httpRequest` | Calls APAC readiness sub-workflow |
| 3 | EMEA Sub-Agent | `n8n-nodes-base.httpRequest` | Calls EMEA readiness sub-workflow |
| 4 | AMER Sub-Agent | `n8n-nodes-base.httpRequest` | Calls AMER readiness sub-workflow |
| 5 | Global Synthesizer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: aggregate + go/no-go |
| 6 | Blockers Check (IF) | `n8n-nodes-base.if` | Branch on blockers present |
| 7 | Exception HITL (Wait) | `n8n-nodes-base.wait` | Wait for exception approval |
| 8 | Exception Handler (IF) | `n8n-nodes-base.if` | Branch approved/denied |
| 9 | Consolidation HITL (Wait) | `n8n-nodes-base.wait` | Wait for consolidation approval |
| 10 | Consolidation Handler (IF) | `n8n-nodes-base.if` | Branch approved/denied |
| 11 | Start Consolidation | `n8n-nodes-base.httpRequest` | POST `startGlobalConsolidation()` |
| 12 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns readiness map |

### Regional Sub-Agent Workflow (shared template)

| # | Node | Type | Purpose |
|---|------|------|---------|
| R1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ region }` |
| R2 | Get Close Status | `n8n-nodes-base.httpRequest` | `getRegionalCloseStatus(region)` |
| R3 | Get Unprocessed | `n8n-nodes-base.httpRequest` | `getUnprocessedTradeCount(region)` |
| R4 | Get Late Materiality | `n8n-nodes-base.httpRequest` | `getLateTradeMateriality(region)` |
| R5 | Get Market Data | `n8n-nodes-base.httpRequest` | `getMarketDataReadiness(region)` |
| R6 | Get Branch Status | `n8n-nodes-base.httpRequest` | `getBranchCompletionStatus(region)` |
| R7 | Regional Assessor (LLM) | `@n8n/n8n-nodes-langchain.agent` | Sonnet: READY/WARNING/BLOCKED |
| R8 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns regional status |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/eod-readiness`
- **Method:** POST
- **Request body:**
  ```json
  {
    "requestType": "ASSESS",
    "regions": ["APAC", "EMEA", "AMER"],
    "correlationId": "corr-eod-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 (per region, parallel) | `getRegionalCloseStatus(region)` | region | close state |
| 2 (per region, parallel) | `getUnprocessedTradeCount(region)` | region | count |
| 3 (per region, parallel) | `getLateTradeMateriality(region)` | region | materiality |
| 4 (per region, parallel) | `getMarketDataReadiness(region)` | region | freshness |
| 5 (per region, parallel) | `getBranchCompletionStatus(region)` | region | branch status |
| 6 (gated) | `startGlobalConsolidation()` | — | consolidation started |

---

## 5. LLM Node Configuration

### Regional Assessor (Node R7)
- **Model tier:** Mid-tier (Sonnet-class)
- **System prompt:** "Assess EOD readiness for region {region}. Given: close status, unprocessed count, late trade materiality, market data readiness, branch completion. Output: status (READY/WARNING/BLOCKED), reasons[], blockers[], materiality. Never assume data — only use provided facts."
- **Temperature:** 0

### Global Synthesizer (Node 5)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are the global EOD readiness supervisor. Aggregate regional assessments into a unified ReadinessMap. Determine go/no-go. Identify critical path (which blocker resolves first). Consider cross-regional dependencies. Output: ReadinessMap JSON with per-region status, overall recommendation, critical path, required actions."
- **Temperature:** 0.1

---

## 6. HITL Gate

- **Exception Gate (Node 7):** After blockers identified, for non-material exceptions
  - Resume: `POST /webhook/eod-exception-approval/{executionId}`
  - Timeout: 1 hour
- **Consolidation Gate (Node 9):** Before `startGlobalConsolidation()`
  - Resume: `POST /webhook/eod-consolidation-approval/{executionId}`
  - Timeout: 2 hours

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Regional sub-agent timeout | Mark region as UNKNOWN; report in map |
| MCP tool failure | Region marked BLOCKED (fail-safe) |
| All regions timeout | Return "Unable to assess readiness" |
| LLM malformed output | Retry once; fallback to raw status data |
| Consolidation call fails | Report failure; suggest manual trigger |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| EOD-EVAL-01 | All-ready | 3 regions ready | GO + consolidation gate |
| EOD-EVAL-02 | Blocked | EMEA blocked | NO-GO with blocker |
| EOD-EVAL-03 | Exception | Non-material APAC issue | Exception approval gate |
| EOD-EVAL-04 | Approved | Exception approved | Re-evaluates to GO |
| EOD-EVAL-05 | Consolidation | Consolidation approved | Calls startGlobalConsolidation |
| EOD-EVAL-06 | Degraded | Market data stale | Region WARNING/BLOCKED |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Regional Assessment | Nodes 2-4 (sub-agents) + R1-R8 template |
| Rq2: Global Consolidation | Node 5 (global synthesizer) |
| Rq3: Exception Approval | Nodes 6-8 (blocker check + HITL) |
| Rq4: Gated Start | Nodes 9-11 (consolidation HITL + execute) |
