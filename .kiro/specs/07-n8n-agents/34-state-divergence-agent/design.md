# Design — State Divergence Agent

## 1. Overview

The State Divergence Agent compares trade state across all storage systems,
detects divergences, classifies root cause, and proposes reconciliation via
HITL gate. Risk M.

**Trigger mechanism:** Webhook (scheduled sweep or on-demand per trade).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Parallel State Fetch]
                           │
       ┌───────────────────┼───────────────────────┐
       ▼                   ▼                       ▼
[Query Postgres]  [Get Mongo Doc]  [Get Redis Cache]
       │                   │                       │
       │            [Get Kafka Event]              │
       │                   │                       │
       │            [Get Analytics]                │
       └───────────────────┼───────────────────────┘
                           ▼
              [Evaluate Canonical State]
                           │
                           ▼
              [LLM: Divergence Analyzer]
                           │
                           ▼
              [IF: Divergence Found?]
                    │          │
               yes          no
                    │          │
        [HITL: Reconciliation] │
            │        │         │
       approved  denied        │
            │        │         │
 [Start Reconciliation] [Hold] │
            │        │         │
            └────────┴─────────┘
                           │
                           ▼
              [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ tradeId, mode, correlationId }` |
| 2 | Query Postgres | `n8n-nodes-base.httpRequest` | GET `queryTradeState()` |
| 3 | Get Mongo Doc | `n8n-nodes-base.httpRequest` | GET `getTradeDocument()` |
| 4 | Get Redis Cache | `n8n-nodes-base.httpRequest` | GET `getCachedTradeState()` |
| 5 | Get Kafka Event | `n8n-nodes-base.httpRequest` | GET `getLatestDomainEvent()` |
| 6 | Get Analytics | `n8n-nodes-base.httpRequest` | GET `getAnalyticsTradeState()` |
| 7 | Evaluate Canonical | `n8n-nodes-base.httpRequest` | POST `evaluateCanonicalState()` |
| 8 | Divergence Analyzer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: classify + explain |
| 9 | Divergence Check (IF) | `n8n-nodes-base.if` | Branch if divergence found |
| 10 | Reconciliation Gate (Wait) | `n8n-nodes-base.wait` | HITL for reconciliation |
| 11 | Approval Handler (IF) | `n8n-nodes-base.if` | Branch approved/denied |
| 12 | Start Reconciliation | `n8n-nodes-base.httpRequest` | POST `startReconciliation()` |
| 13 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns divergence report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/state-divergence`
- **Method:** POST
- **Request body:**
  ```json
  {
    "tradeId": "FX-000042",
    "mode": "SINGLE",
    "correlationId": "corr-state-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 (parallel) | `queryTradeState` | tradeId | Postgres state |
| 1 (parallel) | `getTradeDocument` | tradeId | MongoDB state |
| 1 (parallel) | `getCachedTradeState` | tradeId | Redis state |
| 1 (parallel) | `getLatestDomainEvent` | tradeId | Kafka last event |
| 1 (parallel) | `getAnalyticsTradeState` | tradeId | Analytics state |
| 2 | `evaluateCanonicalState` | all states | canonical + violations |
| 3 (gated) | `startReconciliation` | tradeId, action | reconciliation result |

---

## 5. LLM Node Configuration

### Divergence Analyzer (Node 8)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a state integrity analyst for an FX trade platform. Given: (1) per-system state vector, (2) canonical state evaluation with violated invariants and permitted actions — classify each divergence. Categories: STALE_CACHE (Redis behind), EVENT_LAG (Kafka behind), WRITE_FAILURE (system never received update), SCHEMA_MISMATCH (encoding difference), UNKNOWN. For each: identify stale system, staleness duration, business impact. Never determine the authoritative state yourself — trust evaluateCanonicalState. Output JSON: `{ tradeId, canonicalState, systemStates[], divergences[], businessImpact, recommendedAction, permittedActions[] }`."
- **Temperature:** 0

---

## 6. HITL Gate

- **Placement:** After divergence analysis (Node 10)
- **Condition:** Divergence detected with permitted reconciliation action
- **Wait node:** Resume on `POST /webhook/reconciliation-approval/{executionId}`
- **Timeout:** 4 hours
- **Approval payload:**
  ```json
  {
    "executionId": "exec-state-001",
    "decision": "APPROVED",
    "approverUserId": "user-ops-01",
    "action": "REFRESH_CACHE",
    "tradeId": "FX-000042"
  }
  ```

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| One system unavailable | Report partial comparison; note missing system |
| Canonical evaluation fails | Report raw states without classification |
| Reconciliation fails | Retry once; create manual case |
| All systems consistent | Return "No divergence detected" |
| Trade not found in any system | Return "Trade not found" |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| STATE-EVAL-01 | Cache stale | Redis behind Postgres | STALE_CACHE identified |
| STATE-EVAL-02 | HITL-approve | Reconciliation approved | Calls startReconciliation |
| STATE-EVAL-03 | Consistent | All systems agree | "No divergence" |
| STATE-EVAL-04 | Event lag | Kafka last event stale | EVENT_LAG classified |
| STATE-EVAL-05 | Write failure | Mongo never updated | WRITE_FAILURE + impact |
| STATE-EVAL-06 | Batch sweep | Multiple trades | Per-trade results |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Cross-System Comparison | Nodes 2-7 (parallel fetch + evaluate) |
| Rq2: Divergence Classification | Node 8 (LLM analyzer) |
| Rq3: Reconciliation + HITL | Nodes 9-12 (check + gate + reconcile) |
