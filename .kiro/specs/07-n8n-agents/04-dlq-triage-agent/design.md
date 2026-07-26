# Design — DLQ Triage & Remediation Agent

## 1. Overview

The DLQ Triage Agent is triggered by the `dlq-cluster-analyzer` Python sidecar when pre-clustered failure signatures are ready for triage. It classifies each cluster as transient (replay candidate) or poison (quarantine), optionally checks for state divergence, and proposes remediation — always gated behind human approval before any replay or quarantine executes.

**Trigger mechanism:** Webhook (POST from `dlq-cluster-analyzer` sidecar at `http://fxops-n8n:5678/webhook/dlq-cluster`).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger (from sidecar)] → [Validate Payload (IF)] → [Split Clusters (SplitInBatches)]
                                          │                            │
                                   malformed                          ▼
                                          │              [LLM: Classify Transient vs Poison]
                                          ▼                            │
                               [Log Error & Stop]              ┌──────┴──────┐
                                                        transient         poison
                                                               │              │
                                                               ▼              ▼
                                             [Check Divergence (MCP)]  [Check Divergence (MCP)]
                                                               │              │
                                                               ▼              ▼
                                             [Generate Replay Proposal]  [Generate Quarantine Proposal]
                                                               │              │
                                                               ▼              ▼
                                             [Present HITL Gate]      [Present HITL Gate]
                                                               │              │
                                                               ▼              ▼
                                             [Wait for Approval]      [Wait for Approval]
                                                               │              │
                                                        ┌──────┴──────┐   ┌──┴──────────┐
                                                  approved       denied  approved    denied
                                                        │           │        │          │
                                                        ▼           ▼        ▼          ▼
                                             [Replay (MCP)]  [Log Denial]  [Quarantine (MCP)]  [Log Denial]
                                                        │                        │
                                                        └────────────────────────┘
                                                                     │
                                                                     ▼
                                                        [Respond to Webhook]
```

### Node-by-Node Description:

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives POST from dlq-cluster-analyzer sidecar |
| 2 | Validate Payload | `n8n-nodes-base.if` | Checks required fields: `clusters[]`, each with `cluster_id`, `count`, etc. |
| 3 | Log Error & Stop | `n8n-nodes-base.respondToWebhook` | Returns 400 for malformed payload |
| 4 | Split Clusters | `n8n-nodes-base.splitInBatches` | Iterates over each FailureSignature cluster |
| 5 | Classify Cluster (LLM) | `@n8n/n8n-nodes-langchain.agent` | Deep reasoning: transient vs poison classification |
| 6 | Classification Router | `n8n-nodes-base.switch` | Routes based on classification result |
| 7 | Check Divergence | `n8n-nodes-base.httpRequest` | MCP: `evaluateCanonicalState` on state-reconciliation-service |
| 8 | Generate Replay Proposal | `@n8n/n8n-nodes-langchain.agent` | Deep reasoning: draft replay proposal with impact summary |
| 9 | Generate Quarantine Proposal | `@n8n/n8n-nodes-langchain.agent` | Deep reasoning: draft quarantine explanation |
| 10 | HITL Gate (Replay) | `n8n-nodes-base.wait` | Wait for human approval of replay |
| 11 | HITL Gate (Quarantine) | `n8n-nodes-base.wait` | Wait for human approval of quarantine |
| 12 | Replay Messages | `n8n-nodes-base.httpRequest` | MCP: `replayDlqMessage` (Risk M, with approvalReference) |
| 13 | Quarantine Messages | `n8n-nodes-base.httpRequest` | MCP: `quarantineMessage` (Risk M, with approvalReference) |
| 14 | Log Denial | `n8n-nodes-base.set` | Records denial; marks cluster "reviewed-not-replayed" |
| 15 | Respond to Webhook | `n8n-nodes-base.respondToWebhook` | Returns triage summary to sidecar |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/dlq-cluster`
- **Method:** POST
- **Source:** `dlq-cluster-analyzer` Python sidecar
- **Request body (from sidecar):**
  ```json
  {
    "clusters": [
      {
        "cluster_id": "a1b2c3d4e5f6g7h8",
        "representative_trace": "java.lang.NullPointerException at ...",
        "count": 15,
        "first_seen": "2025-07-25T09:00:00Z",
        "last_seen": "2025-07-25T10:30:00Z",
        "sample_trade_ids": ["FX-000001", "FX-000015", "FX-000042"],
        "affected_topic": "trade-enrichment-dlq",
        "affected_service": "enrichment-service",
        "error_code": "CONNECTION_TIMEOUT"
      }
    ]
  }
  ```

---

## 4. MCP Tool Calls

| Step | Tool | Endpoint | Input | Output |
|------|------|----------|-------|--------|
| 1 | `evaluateCanonicalState` | `http://state-reconciliation-service:8082/mcp/evaluateCanonicalState` | `{ "tradeIds": ["FX-000001", ...] }` | Divergence status: diverged/consistent per trade |
| 2 | `replayDlqMessage` | `http://state-reconciliation-service:8082/mcp/replayDlqMessage` | `{ "clusterId": "...", "messageKeys": [...], "approvalReference": "..." }` | Replay execution result (Risk M) |
| 3 | `quarantineMessage` | `http://state-reconciliation-service:8082/mcp/quarantineMessage` | `{ "clusterId": "...", "messageKeys": [...], "approvalReference": "...", "reason": "..." }` | Quarantine execution result (Risk M) |

---

## 5. LLM Node Configuration

### Classify Cluster (Node 5)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt summary:** "You are a DLQ triage analyst. Given a failure cluster (error signature, count, affected service, timestamps, sample trade IDs), classify it as TRANSIENT or POISON. TRANSIENT: timeout, connection refused, 503, temporary network issues where replay would succeed. POISON: schema validation, deserialization, business rule violation where replay would fail again. Assign confidence (high/medium/low). If confidence is low, default to POISON (safer to quarantine than replay uncertain messages)."
- **Output structure:**
  ```json
  {
    "classification": "TRANSIENT",
    "confidence": "high",
    "reasoning": "ConnectTimeoutException to enrichment-service; service has since recovered based on recent successful messages"
  }
  ```
- **Temperature:** 0

### Generate Replay Proposal (Node 8)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt summary:** "Draft a replay proposal for a transient failure cluster. Include: message count, affected topic/partitions, sample trade IDs (max 10), root cause summary, current downstream service health, estimated impact of replay. Format for human review."
- **Output structure:**
  ```json
  {
    "proposal": "Replay 42 messages from trade-enrichment-dlq. Root cause: ConnectTimeoutException (transient). enrichment-service is now healthy. Impact: 42 trades will resume enrichment processing.",
    "messageCount": 42,
    "affectedTradeIds": ["FX-000001", "..."],
    "riskLevel": "M",
    "divergenceDetected": false
  }
  ```
- **Temperature:** 0.2

### Generate Quarantine Proposal (Node 9)
- **Model tier:** Deep reasoning (Opus-class)  
- **System prompt summary:** "Draft a quarantine explanation for a poison message cluster. Include: root cause (why structural), sample content, recommended investigation steps, owning team/service. Format for human review."
- **Temperature:** 0.2

---

## 6. Memory/Session

- **Within-run:** Clusters processed in batch within a single execution. Classification results accumulate for the final response.
- **Episodic memory (RELATIONAL_STORE):** After triage completion, store:
  - Cluster signature hash
  - Classification (transient/poison)
  - Confidence
  - Outcome (replayed/quarantined/denied)
  - Timestamp
- **Used for:** Future classification — "similar signatures were successfully replayed before"

---

## 7. HITL Gate

- **Placement:** After proposal generation, before any replay or quarantine execution (Nodes 10, 11)
- **Condition:** ALWAYS required — both `replayDlqMessage` and `quarantineMessage` are Risk M
- **Wait node configuration:**
  - Resume on webhook: `POST /webhook/dlq-approval/{executionId}`
  - Timeout: 4 hours (configurable)
- **Approval payload:**
  ```json
  {
    "executionId": "exec-789",
    "clusterId": "a1b2c3d4e5f6g7h8",
    "decision": "APPROVED",
    "approverUserId": "user-ops-03",
    "approvalReference": "apr-ref-101",
    "timestamp": "2025-07-25T11:00:00Z"
  }
  ```
- **Non-bypassable:** No auto-replay regardless of confidence level
- **On denial:** Log denial; mark cluster as "reviewed-not-replayed"; take no action

---

## 8. Error Handling

| Failure Mode | Handling |
|---|---|
| Malformed trigger payload (missing cluster_id) | Return 400; log structured error; do not proceed |
| `evaluateCanonicalState` fails | Proceed without divergence context; note "divergence check unavailable" |
| LLM classification returns malformed JSON | Retry once; if fails, default to POISON (safe default) |
| `replayDlqMessage` fails after approval | Log failure; notify operator; do not retry automatically |
| `quarantineMessage` fails after approval | Log failure; notify operator; leave messages in DLQ |
| HITL approval timeout | Cancel action; mark as "approval-expired"; retain for next triage |

---

## 9. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| DLQ-EVAL-01 | Transient classification | 42 msgs, ConnectTimeoutException, service recovered | Classifies transient; ReplayProposal generated; HITL gate |
| DLQ-EVAL-02 | Poison classification | 5 msgs, SchemaValidationException | Classifies poison; quarantine proposed; HITL gate |
| DLQ-EVAL-03 | Batch triage | 3 clusters: 2 transient, 1 poison | All 3 processed; separate proposals; all gated |
| DLQ-EVAL-04 | Divergence | Transient cluster with state divergence | Divergence context included; priority elevated |
| DLQ-EVAL-05 | Denial | Operator denies replay | Logged; "reviewed-not-replayed"; no action taken |
| DLQ-EVAL-06 | Malformed | Missing cluster_id in payload | Returns 400; logs error; no triage |

---

## 10. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Sidecar-Triggered Activation | Node 1 (Webhook at `/webhook/dlq-cluster`) + Node 2 (payload validation) |
| Rq2: Transient vs Poison Classification | Node 5 (LLM classification with confidence scoring) + Node 6 (router) |
| Rq3: Replay Proposal | Node 8 (proposal generation) + Node 10 (HITL) + Node 12 (execution) |
| Rq4: Quarantine | Node 9 (quarantine explanation) + Node 11 (HITL) + Node 13 (execution) |
| Rq5: Divergence Context Retrieval | Node 7 (evaluateCanonicalState MCP call) |
| Rq6: Model Tier Allocation | Sidecar for clustering; LLM (deep reasoning) only for classification/proposals |
