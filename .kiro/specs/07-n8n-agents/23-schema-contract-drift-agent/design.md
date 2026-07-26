# Design — Schema & Contract Drift Agent

## 1. Overview

The Schema & Contract Drift Agent detects breaking changes in schemas and API
contracts, identifies affected consumers, and flags breaking changes for
review. Risk M with HITL on flag action.

**Trigger mechanism:** Webhook (schema registry event or deployment pipeline hook).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Get Schema Compatibility] → [Find Consumers]
                                                        │
                                                        ▼
                                         [Simulate Payload]
                                                        │
                                                        ▼
                                         [LLM: Impact Analyzer]
                                                        │
                                                        ▼
                                         [IF: Breaking Change?]
                                                  │          │
                                             yes          no
                                                  │          │
                                      [HITL: Flag Approval]  │
                                          │        │         │
                                     approved  denied        │
                                          │        │         │
                               [Flag Breaking]  [Log]        │
                                          │        │         │
                                          └────────┴─────────┘
                                                        │
                                                        ▼
                                         [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ subject, version, type, correlationId }` |
| 2 | Get Schema Compatibility | `n8n-nodes-base.httpRequest` | GET `getSchemaCompatibility(subject, ver)` |
| 3 | Find Consumers | `n8n-nodes-base.httpRequest` | GET `findConsumersOf(topic)` |
| 4 | Simulate Payload | `n8n-nodes-base.httpRequest` | POST `simulatePayloadAgainstConsumers()` |
| 5 | Impact Analyzer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: consumer impact + business flow |
| 6 | Breaking Change Check (IF) | `n8n-nodes-base.if` | Branch if breaking detected |
| 7 | Flag Approval Gate (Wait) | `n8n-nodes-base.wait` | HITL for flag approval |
| 8 | Approval Handler (IF) | `n8n-nodes-base.if` | Branch approved/denied |
| 9 | Flag Breaking Change | `n8n-nodes-base.httpRequest` | POST `flagBreakingChange()` |
| 10 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns drift report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/schema-contract-drift`
- **Method:** POST
- **Request body:**
  ```json
  {
    "subject": "trade-captured-value",
    "version": 4,
    "type": "AVRO",
    "correlationId": "corr-schema-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `getSchemaCompatibility` | subject, version | compatibility result |
| 2 | `findConsumersOf` | topic | consumer list |
| 3 | `simulatePayloadAgainstConsumers` | schema, consumers | pass/fail per consumer |
| 4 (gated) | `flagBreakingChange` | drift details | flag confirmation |

Steps 2-3 depend on step 1 result indicating incompatibility.

---

## 5. LLM Node Configuration

### Impact Analyzer (Node 5)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a schema compatibility analyst for an FX trade platform. Given: (1) compatibility check result, (2) consumer list, (3) payload simulation results — identify which consumers will fail and which business flows (settlement, risk, reporting) will degrade. Rank by criticality. Include rollback guidance (prior version). Never invent consumers or flows. Output JSON: `{ breaking: bool, changes[], affectedConsumers[], businessFlows[], severity, rollbackVersion, summary }`."
- **Temperature:** 0

---

## 6. HITL Gate

- **Placement:** After impact analysis (Node 7)
- **Condition:** Breaking change detected
- **Wait node:** Resume on `POST /webhook/schema-flag-approval/{executionId}`
- **Timeout:** 4 hours (pre-deployment review)
- **Approval payload:**
  ```json
  {
    "executionId": "exec-schema-001",
    "decision": "APPROVED",
    "approverUserId": "user-platform-01",
    "subject": "trade-captured-value"
  }
  ```

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Schema registry unavailable | Return "Unable to check compatibility"; block by default |
| Consumer registry empty | Report "No registered consumers" |
| Simulation timeout | Flag as UNKNOWN_IMPACT; escalate |
| LLM malformed output | Retry once; fallback to raw compatibility result |
| Non-breaking change | Return "Compatible: no downstream impact" |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| SCHEMA-EVAL-01 | Breaking | Field removed | Flagged, consumers listed |
| SCHEMA-EVAL-02 | Compatible | Optional field added | "No impact" |
| SCHEMA-EVAL-03 | Enum | Enum narrowing | Breaking + affected flows |
| SCHEMA-EVAL-04 | HITL-approve | Flag approved | Calls flagBreakingChange |
| SCHEMA-EVAL-05 | OpenAPI | Endpoint contract change | Services identified |
| SCHEMA-EVAL-06 | No consumers | Orphan topic | "No downstream impact" |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Compatibility Check | Node 2 (schema compat) |
| Rq2: Consumer Impact | Nodes 3-5 (consumers + simulation + LLM) |
| Rq3: Breaking-Change Flag | Nodes 6-9 (check + gate + flag) |
