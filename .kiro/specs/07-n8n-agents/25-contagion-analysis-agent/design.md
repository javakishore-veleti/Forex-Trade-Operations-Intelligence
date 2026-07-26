# Design — Contagion Analysis Agent

## 1. Overview

The Contagion Analysis Agent computes the business blast radius of a failure
using graph traversal. It answers "what else is affected?" with quantified
business impact. Risk L — read-only graph queries.

**Trigger mechanism:** Webhook (from Supervisor Agent on BLAST_RADIUS intent).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Find Trade Dependencies] → [Find Affected Books]
                                                        │
                                                        ▼
                                         [Find Downstream Aggregations]
                                                        │
                                                        ▼
                                         [Find Shared Dependencies]
                                                        │
                                                        ▼
                                         [Calculate Blast Radius]
                                                        │
                                                        ▼
                                         [LLM: Impact Narrator]
                                                        │
                                                        ▼
                                         [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ entity, entityType, correlationId }` |
| 2 | Find Trade Dependencies | `n8n-nodes-base.httpRequest` | GET `findTradeDependencies()` |
| 3 | Find Affected Books | `n8n-nodes-base.httpRequest` | GET `findAffectedBooks()` |
| 4 | Find Downstream Aggregations | `n8n-nodes-base.httpRequest` | GET `findDownstreamAggregations()` |
| 5 | Find Shared Dependencies | `n8n-nodes-base.httpRequest` | GET `findSharedMarketDataDependencies()` |
| 6 | Calculate Blast Radius | `n8n-nodes-base.httpRequest` | GET `calculateBusinessBlastRadius()` |
| 7 | Impact Narrator (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: narrative synthesis |
| 8 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns BlastRadiusEnvelope |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/contagion-analysis`
- **Method:** POST
- **Request body:**
  ```json
  {
    "entity": "market-data-feed-reuters",
    "entityType": "FEED",
    "correlationId": "corr-cont-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `findTradeDependencies` | entity, entityType | affected trades |
| 2 | `findAffectedBooks` | entity | books containing affected trades |
| 3 | `findDownstreamAggregations` | entity | reports/EOD depending on entity |
| 4 | `findSharedMarketDataDependencies` | entity | pairs on same source |
| 5 | `calculateBusinessBlastRadius` | entity | counts, notional, regions |

Steps 1-5 can execute with partial parallelization (1-4 parallel, 5 after).

---

## 5. LLM Node Configuration

### Impact Narrator (Node 7)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a contagion analyst for an FX trade operations platform. Given: (1) affected trades, (2) affected books, (3) downstream aggregations, (4) shared dependencies, (5) blast radius metrics — produce a coherent impact narrative. Classify severity: CRITICAL (>1000 trades or >$1B notional), HIGH (>200 trades), MEDIUM (>50), LOW (≤50). Trace the contagion path from source to impact. Never invent entities. Output JSON: `{ severity, tradeCount, notional, regions[], contagionPath[], sharedRisks[], narrative }`."
- **Temperature:** 0.1

---

## 6. HITL Gate

**Not applicable.** This agent is Risk L (read-only graph traversal). No actions are proposed or executed.

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Graph store unavailable | Return "Unable to compute blast radius" |
| Entity not found in graph | Return "Entity not tracked in dependency graph" |
| Partial graph data | Proceed with available data; note gaps |
| LLM malformed output | Retry once; fallback to raw metrics |
| Zero affected entities | Return "No downstream impact detected" |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| CONT-EVAL-01 | Feed failure | market-data-feed-X | Pairs, trades, books listed |
| CONT-EVAL-02 | Counterparty | FX-CP-003 | Affected trades + settlement |
| CONT-EVAL-03 | Service | enrichment-svc | Blocked trades |
| CONT-EVAL-04 | Shared feed | 5 pairs on one source | Concentration flagged |
| CONT-EVAL-05 | Minor | 3 trades affected | LOW severity |
| CONT-EVAL-06 | Cascade | Multi-hop, multi-region | CRITICAL severity |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Blast Radius | Nodes 2-6 (graph queries + aggregation) |
| Rq2: Shared Dependencies | Node 5 (shared market-data deps) |
| Rq3: Business Impact | Node 6 (calculate) + Node 7 (LLM narrative) |
