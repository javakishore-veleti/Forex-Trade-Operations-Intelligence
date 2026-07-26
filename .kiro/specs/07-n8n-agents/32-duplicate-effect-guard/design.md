# Design — Duplicate Business-Effect Guard Agent

## 1. Overview

The Duplicate Business-Effect Guard detects double-booking and duplicate
settlement effects, classifies real vs benign, and proposes dry-run reversal
via HITL gate. Risk H.

**Trigger mechanism:** Webhook (from replay event or idempotency-collision alert).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Check Idempotency] → [Find Double Booking]
                                                    │
                                                    ▼
                                     [Find Duplicate Settlement]
                                                    │
                                                    ▼
                                     [LLM: Effect Classifier]
                                                    │
                                                    ▼
                                     [IF: Real Double?]
                                              │          │
                                         yes          no
                                              │          │
                                  [Dry-Run Reversal]     │
                                              │          │
                                  [HITL: Reversal]       │
                                      │        │         │
                                 approved  denied        │
                                      │        │         │
                           [Execute Reversal]  [Hold]    │
                                      │        │         │
                                      └────────┴─────────┘
                                                    │
                                                    ▼
                                     [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ tradeId, key, correlationId }` |
| 2 | Check Idempotency | `n8n-nodes-base.httpRequest` | GET `checkIdempotencyConsumed(key)` |
| 3 | Find Double Booking | `n8n-nodes-base.httpRequest` | GET `findDoubleBooking(tradeId)` |
| 4 | Find Duplicate Settlement | `n8n-nodes-base.httpRequest` | GET `findDuplicateSettlementInstruction()` |
| 5 | Effect Classifier (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: real vs benign |
| 6 | Real Double Check (IF) | `n8n-nodes-base.if` | Branch if REAL_DOUBLE_EFFECT |
| 7 | Dry-Run Reversal | `n8n-nodes-base.httpRequest` | POST `reverseDuplicateEffect(dryRun=true)` |
| 8 | Reversal Gate (Wait) | `n8n-nodes-base.wait` | HITL for reversal approval |
| 9 | Approval Handler (IF) | `n8n-nodes-base.if` | Branch approved/denied |
| 10 | Execute Reversal | `n8n-nodes-base.httpRequest` | POST `reverseDuplicateEffect(dryRun=false)` |
| 11 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns duplicate report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/duplicate-effect-guard`
- **Method:** POST
- **Request body:**
  ```json
  {
    "tradeId": "FX-000042",
    "key": "idem-fx-000042-v2",
    "triggerType": "REPLAY",
    "correlationId": "corr-dup-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `checkIdempotencyConsumed` | key | consumed flag + metadata |
| 2 | `findDoubleBooking` | tradeId | duplicate positions |
| 3 | `findDuplicateSettlementInstruction` | tradeId | duplicate SIs |
| 4 (dry-run) | `reverseDuplicateEffect(dryRun=true)` | duplicates | impact preview |
| 5 (gated) | `reverseDuplicateEffect(dryRun=false)` | approved plan | reversal result |

Steps 1-3 can execute in parallel.

---

## 5. LLM Node Configuration

### Effect Classifier (Node 5)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a transaction integrity analyst for an FX platform. Given: (1) idempotency check result, (2) double-booking results, (3) duplicate settlement check — classify as REAL_DOUBLE_EFFECT (both created financial positions/settlements that should be one) or BENIGN_RETRY (idempotent, no new effect). For REAL_DOUBLE: quantify financial exposure (notional × 2 vs expected), identify which record is the duplicate. For conflicts: explain divergent payloads. Output JSON: `{ classification, financialExposure, duplicateRecordId, explanation, confidence }`."
- **Temperature:** 0

---

## 6. HITL Gate

- **Placement:** After dry-run reversal result (Node 8)
- **Condition:** REAL_DOUBLE_EFFECT confirmed
- **Wait node:** Resume on `POST /webhook/reversal-approval/{executionId}`
- **Timeout:** 2 hours (critical financial action)
- **Approval payload:**
  ```json
  {
    "executionId": "exec-dup-001",
    "decision": "APPROVED",
    "approverUserId": "user-settle-01",
    "duplicateRecordId": "rec-dup-001"
  }
  ```

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Idempotency store unavailable | Flag as UNKNOWN; escalate immediately |
| Double-booking check fails | Alert ops; cannot confirm safety |
| Dry-run fails | Report without reversal option; escalate |
| Reversal fails | Retry once; create manual remediation case |
| No duplicate detected | Return "No duplicate effect found" |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| DUP-EVAL-01 | Real double | Replay → double settlement | REAL detected |
| DUP-EVAL-02 | Dry-run | Reversal preview | Impact shown |
| DUP-EVAL-03 | HITL-approve | Reversal approved | Executes reversal |
| DUP-EVAL-04 | Benign | Idempotent retry | BENIGN, no action |
| DUP-EVAL-05 | Conflict | Key collision, different payload | Conflict flagged |
| DUP-EVAL-06 | HITL-deny | Reversal denied | Escalates |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Double-Booking Detection | Nodes 2-4 (checks) |
| Rq2: Real vs Benign | Node 5 (LLM classifier) |
| Rq3: Dry-Run Reversal + HITL | Nodes 6-10 (check + dry-run + gate + execute) |
