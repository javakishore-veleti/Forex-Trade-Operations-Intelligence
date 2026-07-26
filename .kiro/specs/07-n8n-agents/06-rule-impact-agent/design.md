# Design — Runtime Business Rule Impact Agent

## 1. Overview

The Rule Impact Agent detects post-deployment rule firing anomalies, compares
pre/post behavior, identifies affected trades, and proposes human-gated
rollback. Triggered by a Python sidecar anomaly envelope.

**Trigger mechanism:** Webhook (POST from firing-rate-anomaly-detector sidecar).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Fetch Firing Stats] → [Compare Behavior] → [Find Conflicts]
                                                                        │
                                                                        ▼
                                                            [LLM: Impact Analyzer]
                                                                        │
                                                                        ▼
                                                            [Simulate Rollback]
                                                                        │
                                                                        ▼
                                                            [Similar Defect Retrieval]
                                                                        │
                                                                        ▼
                                                            [LLM: Rollback Proposal]
                                                                        │
                                                                        ▼
                                                            [HITL Gate (IF Risk H)]
                                                                  │           │
                                                           risk H         no action needed
                                                                  │           │
                                                           [Wait Node]        │
                                                                  │           │
                                                           [Approval Check]   │
                                                              │       │       │
                                                        approved   denied     │
                                                              │       │       │
                                                  [Execute Rollback]  [Log]   │
                                                              │       │       │
                                                              └───────┴───────┘
                                                                        │
                                                                        ▼
                                                            [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives anomaly envelope from sidecar |
| 2 | Fetch Firing Stats | `n8n-nodes-base.httpRequest` | GET `getRuleFiringStats()` via MCP |
| 3 | Compare Behavior | `n8n-nodes-base.httpRequest` | GET `compareRuleBehavior(preVer, postVer)` |
| 4 | Find Conflicts | `n8n-nodes-base.httpRequest` | GET `findConflictingRules()` |
| 5 | Impact Analyzer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: causal analysis of anomaly |
| 6 | Simulate Rollback | `n8n-nodes-base.httpRequest` | GET `simulateRule()` with previous version |
| 7 | Similar Defect Retrieval | `n8n-nodes-base.httpRequest` | Vector search for prior rule anomalies |
| 8 | Rollback Proposal (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: structured rollback proposal |
| 9 | HITL Gate (IF) | `n8n-nodes-base.if` | Checks if rollback is recommended |
| 10 | Wait for Approval | `n8n-nodes-base.wait` | Pauses for human decision |
| 11 | Approval Handler (IF) | `n8n-nodes-base.if` | Routes approved vs denied |
| 12 | Execute Rollback | `n8n-nodes-base.httpRequest` | POST `requestRuleRollback()` with approval ref |
| 13 | Log Denial | `n8n-nodes-base.httpRequest` | Logs denial to audit store |
| 14 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns impact report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/rule-impact`
- **Method:** POST
- **Request body:**
  ```json
  {
    "anomalyType": "OVER_FIRING",
    "ruleId": "FX-RISK-EUR-001",
    "ruleVersion": "v7.14",
    "priorVersion": "v7.13",
    "deviationPct": 28.4,
    "affectedPairs": ["EUR/GBP", "EUR/USD"],
    "detectedAt": "2025-07-25T14:05:00Z",
    "correlationId": "corr-rule-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `getRuleFiringStats()` | ruleId, timeWindow | firing counts, affected trades |
| 2 | `compareRuleBehavior(v7.13, v7.14)` | pre/post versions | delta metrics |
| 3 | `findConflictingRules()` | ruleId | overlapping rule conditions |
| 4 | `simulateRule()` | previous version, sample trades | expected behavior |
| 5 | `requestRuleRollback()` | ruleId, targetVersion, approvalRef | rollback confirmation (gated) |

Steps 2-3 execute in parallel.

---

## 5. LLM Node Configuration

### Impact Analyzer (Node 5)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a rule impact analyst for an FX platform. Given firing stats, behavior comparison, and conflict analysis, determine: (1) root cause of the anomaly, (2) affected scope (pairs, books, regions), (3) business impact (trades rejected, exposure change), (4) classification: over-firing/under-firing/pattern-shift. Output structured JSON."
- **Temperature:** 0

### Rollback Proposal (Node 8)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "Given the impact analysis, simulation results, and similar historical defects, produce a rollback proposal. Include: target version, estimated impact of rollback, risk of keeping current version, trades that would be re-evaluated. Never recommend rollback unless the anomaly is confirmed material. Output as RollbackProposal envelope."
- **Temperature:** 0.1

---

## 6. HITL Gate

- **Placement:** After Rollback Proposal (Node 9)
- **Condition:** LLM recommends rollback AND anomaly is material
- **Wait node:** Resume on `POST /webhook/rule-impact-approval/{executionId}`
- **Timeout:** 2 hours
- **Approval payload:**
  ```json
  {
    "executionId": "exec-456",
    "decision": "APPROVED",
    "approverUserId": "user-rules-01",
    "approvalReference": "apr-rule-789"
  }
  ```
- **Non-bypassable:** Rule rollback always requires explicit approval

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Firing stats unavailable | Report anomaly envelope data only; suggest manual investigation |
| Simulation fails | Skip simulation; note in proposal that impact is estimated |
| Rollback execution fails | Report failure; suggest manual rollback via rules console |
| LLM malformed output | Retry once; fallback to raw comparison data |
| Approval timeout | Cancel rollback proposal; notify via alert |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| RULE-EVAL-01 | Integration | Over-firing envelope for v7.14 | Impact report + rollback proposal |
| RULE-EVAL-02 | No-anomaly | Normal firing rate | "No material anomaly" response |
| RULE-EVAL-03 | Conflicts | Conflicting rules detected | Explanation without rollback |
| RULE-EVAL-04 | HITL-approve | Rollback approved | Executes requestRuleRollback |
| RULE-EVAL-05 | HITL-deny | Rollback denied | Logs denial, monitoring mode |
| RULE-EVAL-06 | RAG | Similar defect query | Returns prior defect cases |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Firing Pattern Anomaly | Nodes 1 (trigger) + 2 (stats) + 5 (analysis) |
| Rq2: Pre/Post Comparison | Nodes 3 (compare) + 4 (conflicts) |
| Rq3: Rollback Proposal + HITL | Nodes 6 (simulate) + 8 (proposal) + 9-12 (gate) |
| Rq4: Similar Defects | Node 7 (vector retrieval) |
