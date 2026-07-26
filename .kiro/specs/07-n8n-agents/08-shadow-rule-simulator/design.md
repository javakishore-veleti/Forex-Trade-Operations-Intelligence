# Design — Shadow Rule Simulator Agent

## 1. Overview

The Shadow Rule Simulator converts NL rule descriptions to DRL, deploys to a
sandbox, replays historical events, computes an impact diff, and gates
production deployment behind human approval. Risk H due to eventual prod deploy.

**Trigger mechanism:** Webhook (analyst request via Supervisor or direct API).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [DRL Corpus Retrieval] → [LLM: NL→DRL Generator] → [Validate DRL]
                                                                              │
                                                                    ┌────── (valid?) ──────┐
                                                                    │                      │
                                                                  valid               invalid
                                                                    │                      │
                                                                    │        [LLM: Reflection Fix] ←──┐
                                                                    │                      │           │
                                                                    │              [Re-validate] ──────┘
                                                                    │                      │         (max 3)
                                                                    ▼                      ▼
                                                        [Load Shadow Rule]          [Fail Response]
                                                                    │
                                                                    ▼
                                                        [Replay Historical Events]
                                                                    │
                                                                    ▼
                                                        [Read Shadow Results]
                                                                    │
                                                                    ▼
                                                        [Diff Against Production]
                                                                    │
                                                                    ▼
                                                        [LLM: Impact Explainer]
                                                                    │
                                                                    ▼
                                                        [HITL Gate - Deploy Approval]
                                                              │            │
                                                        approved        denied
                                                              │            │
                                                    [Hand Off to Deploy]  [Archive]
                                                              │            │
                                                              └────────────┘
                                                                    │
                                                                    ▼
                                                        [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives NL rule request |
| 2 | DRL Corpus Retrieval | `n8n-nodes-base.httpRequest` | Fetch similar DRL examples via embeddings |
| 3 | NL→DRL Generator (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: generate DRL from NL |
| 4 | Validate DRL | `n8n-nodes-base.httpRequest` | POST to shadow pod for parse/compile |
| 5 | Validation Check (IF) | `n8n-nodes-base.if` | Branch on valid/invalid |
| 6 | Reflection Fix (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: fix DRL based on error |
| 7 | Re-validate | `n8n-nodes-base.httpRequest` | Re-validate corrected DRL |
| 8 | Retry Counter (IF) | `n8n-nodes-base.if` | Max 3 reflection iterations |
| 9 | Load Shadow Rule | `n8n-nodes-base.httpRequest` | POST `loadShadowRule(drl)` |
| 10 | Replay Events | `n8n-nodes-base.httpRequest` | POST `replayHistoricalEvents(window)` |
| 11 | Read Shadow Results | `n8n-nodes-base.httpRequest` | GET `readShadowRiskResults()` |
| 12 | Diff Against Production | `n8n-nodes-base.httpRequest` | GET `diffAgainstProduction()` |
| 13 | Impact Explainer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: explain diff in business terms |
| 14 | HITL Gate (Wait) | `n8n-nodes-base.wait` | Wait for deploy approval |
| 15 | Approval Check (IF) | `n8n-nodes-base.if` | Branch on approved/denied |
| 16 | Hand Off to Deploy | `n8n-nodes-base.httpRequest` | Notify deploy pipeline |
| 17 | Archive Results | `n8n-nodes-base.httpRequest` | Store shadow results for reference |
| 18 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns impact report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/shadow-rule-simulator`
- **Method:** POST
- **Request body:**
  ```json
  {
    "ruleDescription": "Reject EUR/TRY trades above 5M notional when volatility exceeds 3 standard deviations",
    "replayWindow": "5d",
    "requestedBy": "user-rules-01",
    "correlationId": "corr-shadow-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | (embedding search) | NL description | Similar DRL examples |
| 2 | (LLM generation) | NL + examples | DRL source code |
| 3 | `loadShadowRule(drl)` | validated DRL | deployment confirmation |
| 4 | `replayHistoricalEvents(window)` | time window | replay status |
| 5 | `readShadowRiskResults()` | — | shadow decision outcomes |
| 6 | `diffAgainstProduction()` | — | impact diff |

---

## 5. LLM Node Configuration

### NL→DRL Generator (Node 3)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "Generate a valid Drools DRL rule from the natural-language description. Use the provided DRL examples as style reference. Output ONLY the DRL source code. Ensure proper package declaration, imports, rule name, when/then structure."
- **Temperature:** 0.1

### Reflection Fix (Node 6)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "The DRL rule failed validation with the following error. Fix the DRL while preserving the original intent. Output ONLY the corrected DRL source code."
- **Temperature:** 0

### Impact Explainer (Node 13)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "Explain the shadow rule impact diff in business terms. Include: (1) trades newly rejected/accepted, (2) risk score changes, (3) affected pairs/books/regions, (4) edge cases found. Never minimize impact. Be precise about numbers."
- **Temperature:** 0.2

---

## 6. HITL Gate

- **Placement:** After Impact Explainer (Node 14)
- **Condition:** Always (production deploy is always gated)
- **Wait node:** Resume on `POST /webhook/shadow-rule-approval/{executionId}`
- **Timeout:** 24 hours (rule review may take longer)
- **Approval payload:**
  ```json
  {
    "executionId": "exec-shadow-001",
    "decision": "APPROVED",
    "approverUserId": "user-governance-01",
    "approvalReference": "apr-shadow-001"
  }
  ```

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| NL too vague to generate DRL | Ask for clarification with specific questions |
| DRL fails after 3 reflection attempts | Return failure with last error; suggest manual authoring |
| Shadow pod unavailable | Queue request; notify analyst of delay |
| Replay produces no data | Report "no matching events in window"; suggest wider window |
| Diff computation timeout | Return partial results with warning |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| SHAD-EVAL-01 | E2E | Valid NL rule description | DRL generated, validated, replayed, diff shown |
| SHAD-EVAL-02 | Clarification | Vague NL input | Asks for specifics |
| SHAD-EVAL-03 | Reflection | Invalid DRL generated | Self-corrects within 3 attempts |
| SHAD-EVAL-04 | Zero-impact | Rule affects no historical trades | "No trades affected" report |
| SHAD-EVAL-05 | HITL-approve | Deploy approved | Hands off artifact |
| SHAD-EVAL-06 | HITL-deny | Deploy denied | Archives, logs denial |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: NL→DRL Conversion | Nodes 2 (corpus) + 3 (generator) + 4-8 (validation/reflection) |
| Rq2: Shadow Deploy + Replay | Nodes 9 (load) + 10 (replay) |
| Rq3: Impact Diff | Nodes 11 (results) + 12 (diff) + 13 (explain) |
| Rq4: Gated Deploy | Nodes 14-17 (HITL gate + approval routing) |
