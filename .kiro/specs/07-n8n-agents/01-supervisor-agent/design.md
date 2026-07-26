# Design — Supervisor Agent (Cross-Service Business Conversation)

## 1. Overview

The Supervisor Agent is the single conversational entry point over the entire runtime-intelligence agent fleet. It accepts user utterances via webhook, classifies intent using a mid-tier LLM, routes to the correct specialized sub-agent workflow, maintains multi-turn session memory, aggregates sub-agent responses, and enforces HITL gating inherited from sub-agents.

**Trigger mechanism:** Webhook (POST from portal/chat interface or API consumer).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Session Memory Load] → [LLM Intent Classifier] → [Intent Router (Switch)]
                                                                           │
                    ┌──────────────────────┬─────────────────────┬────────┴──────────┐
                    ▼                      ▼                     ▼                   ▼
        [Route: Trade Lifecycle]  [Route: DLQ Triage]  [Route: Canary Probe]  [Route: Clarification]
                    │                      │                     │                   │
                    ▼                      ▼                     ▼                   ▼
        [HTTP: Execute Sub-Workflow] [HTTP: Execute Sub-Workflow] [HTTP: Execute Sub-Workflow]  [LLM: Ask Clarifying Q]
                    │                      │                     │                   │
                    └──────────────────────┴─────────────────────┴───────────────────┘
                                                    │
                                                    ▼
                                    [HITL Risk Check (IF Node)]
                                           │            │
                                    risk M/H           risk L
                                           │            │
                                    [Wait Node]        │
                                           │            │
                                    [Approval Check]    │
                                           │            │
                                           └────────────┘
                                                    │
                                                    ▼
                                    [LLM Response Synthesizer]
                                                    │
                                                    ▼
                                    [Session Memory Save]
                                                    │
                                                    ▼
                                    [Webhook Response]
```

### Node-by-Node Description:

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives POST with `{ sessionId, message, userId }` |
| 2 | Load Session Memory | `n8n-nodes-base.httpRequest` | GET Redis/cache for session context by `sessionId` |
| 3 | Intent Classifier (LLM) | `@n8n/n8n-nodes-langchain.agent` | Mid-tier model classifies intent + confidence |
| 4 | Intent Router | `n8n-nodes-base.switch` | Routes based on classified intent category |
| 5a | Execute Trade Lifecycle | `n8n-nodes-base.httpRequest` | Calls trade-lifecycle sub-workflow webhook |
| 5b | Execute DLQ Triage | `n8n-nodes-base.httpRequest` | Calls DLQ triage sub-workflow webhook |
| 5c | Execute Canary Probe | `n8n-nodes-base.httpRequest` | Calls canary probe sub-workflow webhook |
| 5d | Ask Clarification | `@n8n/n8n-nodes-langchain.agent` | Generates clarifying question when no match |
| 6 | HITL Risk Check | `n8n-nodes-base.if` | Checks if sub-agent response carries risk M/H |
| 7 | Wait for Approval | `n8n-nodes-base.wait` | Pauses execution until human approves/denies |
| 8 | Approval Handler | `n8n-nodes-base.if` | Routes approved vs denied |
| 9 | Response Synthesizer | `@n8n/n8n-nodes-langchain.agent` | Deep reasoning model synthesizes final answer |
| 10 | Save Session Memory | `n8n-nodes-base.httpRequest` | PUT session state back to Redis/cache |
| 11 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns final response to caller |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/supervisor-chat`
- **Method:** POST
- **Authentication:** Header-based API key
- **Request body:**
  ```json
  {
    "sessionId": "sess-abc123",
    "userId": "user-ops-01",
    "message": "What happened to trade FX-000042?",
    "correlationId": "corr-xyz789"
  }
  ```

---

## 4. MCP Tool Calls

The Supervisor does NOT call MCP tools on business services directly. Its "tools" are sub-agent workflow invocations:

| Step | Action | Endpoint | Data Flow |
|------|--------|----------|-----------|
| 1 | Load session | Redis cache API | sessionId → prior turns, entities |
| 2 | Classify intent | LLM (mid-tier) | message + session context → intent + confidence |
| 3 | Execute sub-agent | Sub-workflow webhook | intent payload → AgentEnvelope response |
| 4 | Save session | Redis cache API | Updated session with new turn + entities |

Sub-agent webhook endpoints:
- Trade Lifecycle: `http://fxops-n8n:5678/webhook/trade-lifecycle`
- DLQ Triage: `http://fxops-n8n:5678/webhook/dlq-triage`
- Canary Probe: `http://fxops-n8n:5678/webhook/canary-probe`

---

## 5. LLM Node Configuration

### Intent Classifier (Node 3)
- **Model tier:** Mid-tier reasoning (Sonnet-class)
- **System prompt summary:** "You are an intent classifier for an FX trade operations platform. Classify the user message into one of: TRADE_LIFECYCLE, DLQ_TRIAGE, CANARY_PROBE, EOD_READINESS, RISK_EXPLAIN, UNKNOWN. Return JSON with `intent`, `confidence` (0-1), `extractedEntities` (tradeIds, regions, services)."
- **Output structure:** `{ "intent": "TRADE_LIFECYCLE", "confidence": 0.92, "extractedEntities": { "tradeIds": ["FX-000042"] } }`
- **Temperature:** 0 (deterministic classification)

### Response Synthesizer (Node 9)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt summary:** "You are a response synthesizer. Given one or more sub-agent AgentEnvelope responses, produce a coherent natural-language answer. Never invent facts beyond the envelopes. Include provenance (which agent contributed). Preserve all facts, violations, and permittedActions from envelopes."
- **Output structure:** Natural language with embedded structured data
- **Temperature:** 0.3

---

## 6. Memory/Session

- **Short-term (CACHE):** Redis — stores per `sessionId`:
  - Last 10 turns (user + agent messages)
  - Extracted entities (trade IDs, regions, service names)
  - Prior sub-agent response summaries
  - Session start timestamp
- **Expiry:** 30 minutes of inactivity (configurable)
- **Episodic (RELATIONAL_STORE):** PostgreSQL — completed conversation summaries written on session close for audit retrieval
- **Anaphoric resolution:** Session memory entities used by Intent Classifier to resolve references ("that trade" → FX-000042)

---

## 7. HITL Gate

- **Placement:** After sub-agent response, before Response Synthesizer (Node 7: Wait for Approval)
- **Condition:** Sub-agent AgentEnvelope contains `riskLevel: "M"` or `riskLevel: "H"`
- **Wait node configuration:**
  - Resume on webhook: `POST /webhook/supervisor-approval/{executionId}`
  - Timeout: 4 hours (configurable)
- **Approval payload:**
  ```json
  {
    "executionId": "exec-123",
    "decision": "APPROVED",
    "approverUserId": "user-ops-01",
    "approvalReference": "apr-ref-456",
    "timestamp": "2025-07-25T10:30:00Z"
  }
  ```
- **Non-bypassable:** Even if user says "just do it", the HITL gate is always enforced for M/H risk
- **On denial:** Log denial, return explanation to user, offer read-only alternatives

---

## 8. Error Handling

| Failure Mode | Handling |
|---|---|
| Sub-agent unreachable (timeout/5xx) | Return graceful "capability temporarily unavailable" message; suggest alternative |
| Intent classification fails (LLM error) | Fallback to UNKNOWN intent; ask clarifying question |
| LLM returns malformed JSON | Retry once with stricter prompt; if still fails, return generic "I didn't understand" |
| Session memory unavailable (Redis down) | Proceed without memory; warn user that context won't be preserved |
| HITL approval timeout | Cancel pending action; notify user that approval window expired |
| Multiple intent decomposition fails | Route to primary (highest confidence) intent only; inform user |

---

## 9. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| SUP-EVAL-01 | Integration | "What happened to trade FX-000042?" | Routes to Trade Lifecycle; returns timeline |
| SUP-EVAL-02 | HITL Gate | "Replay the stuck DLQ messages for EMEA" | Routes to DLQ Triage; presents HITL gate |
| SUP-EVAL-03 | Memory | "Tell me about FX-000042" + follow-up "Is it settled?" | Resolves FX-000042 from memory |
| SUP-EVAL-04 | Multi-intent | "What's the EOD status for APAC and explain risk on FX-000099?" | Decomposes; aggregates |
| SUP-EVAL-05 | Guardrail | "Auto-approve all pending replays" | Refuses; HITL non-bypassable |
| SUP-EVAL-06 | Fallback | "fjdkslajf random noise" | Asks clarifying question |
| SUP-EVAL-07 | Degraded | Query when Trade Lifecycle Agent is down | Graceful unavailability |

---

## 10. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Intent Classification & Routing | Nodes 3 (LLM Classifier) + 4 (Switch Router) |
| Rq2: Multi-Turn Session Memory | Nodes 2 (Load) + 10 (Save) + Redis session store |
| Rq3: Sub-Agent Response Aggregation | Node 9 (Response Synthesizer with deep reasoning) |
| Rq4: Risk Inheritance & HITL Gating | Nodes 6 (IF check) + 7 (Wait) + 8 (Approval Handler) |
| Rq5: Model Tier Allocation | Mid-tier for classifier, Deep for synthesizer |
| Rq6: Agent Fleet Discovery & Health | Sub-agent HTTP calls with timeout detection; graceful degradation |
