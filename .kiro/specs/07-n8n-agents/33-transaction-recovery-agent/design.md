# Design — Transaction Recovery Coordinator Agent

## 1. Overview

The Transaction Recovery Coordinator is a multi-phase agent that investigates
stuck transactions, plans recovery, verifies safety, and executes step-by-step
with HITL gates. Risk H.

**Trigger mechanism:** Webhook (from investigation conclusion or recovery request).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Investigation Phase]
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
    [Verify No Settlement] [Check Replay Key] [Gather State]
              └────────────┼────────────┘
                           ▼
              [LLM: Investigation Report]
                           │
                           ▼
              [LLM: Recovery Planner]
                           │
                           ▼
              [HITL: Plan Approval]
                   │          │
              approved     denied
                   │          │
              [Step Loop]   [Close]
                   │
              [Safety Check] → [HITL: Step Approval] → [Execute Step]
                   │                                         │
                   └─────────────── [Verify Post-Condition] ─┘
                                          │
                                          ▼
                              [Close Recovery Case]
                                          │
                                          ▼
                              [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ tradeId, requestType, correlationId }` |
| 2 | Verify No Settlement | `n8n-nodes-base.httpRequest` | GET `verifyNoSettlement()` |
| 3 | Check Replay Key | `n8n-nodes-base.httpRequest` | GET `checkReplayKey()` |
| 4 | Gather State | `n8n-nodes-base.httpRequest` | GET state from multiple systems |
| 5 | Investigation LLM | `@n8n/n8n-nodes-langchain.agent` | Opus: investigation report |
| 6 | Recovery Planner LLM | `@n8n/n8n-nodes-langchain.agent` | Opus: ordered plan |
| 7 | Plan Approval Gate (Wait) | `n8n-nodes-base.wait` | HITL for plan approval |
| 8 | Plan Approval IF | `n8n-nodes-base.if` | Branch approved/denied |
| 9 | Step Loop | `n8n-nodes-base.splitInBatches` | Iterate recovery steps |
| 10 | Safety Check | `n8n-nodes-base.httpRequest` | Pre-step verification |
| 11 | Step Approval Gate (Wait) | `n8n-nodes-base.wait` | HITL per high-risk step |
| 12 | Execute Step | `n8n-nodes-base.httpRequest` | Execute recovery action |
| 13 | Verify Post-Condition | `n8n-nodes-base.httpRequest` | POST `compareState()` |
| 14 | Close Recovery Case | `n8n-nodes-base.httpRequest` | POST `closeRecoveryCase()` |
| 15 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns recovery report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/transaction-recovery`
- **Method:** POST
- **Request body:**
  ```json
  {
    "tradeId": "FX-000042",
    "requestType": "RECOVERY",
    "correlationId": "corr-recov-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `verifyNoSettlement` | tradeId | settlement status |
| 2 | `checkReplayKey` | tradeId | replay idempotency |
| 3 | (multiple state queries) | tradeId | per-system states |
| 4 (gated) | `invalidateCache` | tradeId | cache cleared |
| 5 (gated) | `replayEvent` | tradeId, eventId | replay result |
| 6 | `compareState` | tradeId | verification result |
| 7 | `closeRecoveryCase` | caseId | case closed |

---

## 5. LLM Node Configuration

### Investigation Report (Node 5)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a transaction investigator for an FX platform. Given: settlement status, replay key status, and multi-system state — produce an investigation report. Identify: which system is divergent, what's missing, what likely happened. Output JSON: `{ tradeId, systemStates[], divergences[], probableCause, canRecover: bool, recoveryOptions[] }`."
- **Temperature:** 0

### Recovery Planner (Node 6)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a recovery planner. Given: investigation report — produce an ordered recovery plan. Each step must be: narrow (one action), idempotent, verifiable. Include pre-conditions, action, expected-post-condition, abort-if. Output JSON: `{ plan: [{ stepId, action, tool, params, precondition, expectedResult, abortIf }], totalSteps, estimatedDurationMin, risks[] }`."
- **Temperature:** 0

---

## 6. HITL Gate

- **Plan-level gate (Node 7):** Before any execution begins
  - **Wait node:** Resume on `POST /webhook/recovery-plan-approval/{executionId}`
  - **Timeout:** 4 hours
- **Step-level gate (Node 11):** Per high-risk step (replayEvent, invalidateCache)
  - **Wait node:** Resume on `POST /webhook/recovery-step-approval/{executionId}/{stepId}`
  - **Timeout:** 1 hour per step

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Settlement already occurred | HALT — cannot recover; report to manager |
| Pre-condition fails | HALT step; escalate with current state |
| Step execution fails | HALT; do not proceed; report partial state |
| Post-condition verification fails | HALT; flag unexpected state |
| All steps succeed | Close case with full audit |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| RECOV-EVAL-01 | Full recovery | Stuck trade | Plan → approve → execute |
| RECOV-EVAL-02 | Safety block | Settlement exists | Recovery blocked |
| RECOV-EVAL-03 | Step fail | Step verification fails | Halts, escalates |
| RECOV-EVAL-04 | Plan denied | Approver rejects | Case closed, logged |
| RECOV-EVAL-05 | Simple | Cache-only issue | 1-step plan |
| RECOV-EVAL-06 | Complex | Multi-system diverge | Multi-step plan |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Investigation | Nodes 2-5 (checks + investigation LLM) |
| Rq2: Recovery Planning | Node 6 (planner LLM) |
| Rq3: Safety Verification | Nodes 10, 13 (pre/post checks) |
| Rq4: HITL Step Execution | Nodes 7-12 (plan gate + step loop + step gate) |
