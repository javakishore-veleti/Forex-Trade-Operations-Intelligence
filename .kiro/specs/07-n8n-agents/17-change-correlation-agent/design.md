# Design — Runtime Change Correlation Agent

## 1. Overview

The Change Correlation Agent identifies which infrastructure/config changes
caused observed business behavior shifts. It gathers recent changes, computes
correlation, and explains causal hypotheses. Risk L — advisory only.

**Trigger mechanism:** Webhook (behavior shift detected by sidecar or on-demand query).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Get Recent Changes] → [Correlate Change to Outcome] → [Get Change Graph]
                                                                                   │
                                                                                   ▼
                                                                   [Similar Incident Retrieval]
                                                                                   │
                                                                                   ▼
                                                                   [LLM: Causal Analyzer]
                                                                                   │
                                                                                   ▼
                                                                   [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives behavior shift envelope |
| 2 | Get Recent Changes | `n8n-nodes-base.httpRequest` | GET `getRecentChanges(window)` |
| 3 | Correlate Change | `n8n-nodes-base.httpRequest` | GET `correlateChangeToOutcome()` |
| 4 | Get Change Graph | `n8n-nodes-base.httpRequest` | GET `getChangeGraph(entity)` |
| 5 | Similar Incident Retrieval | `n8n-nodes-base.httpRequest` | Vector search for prior patterns |
| 6 | Causal Analyzer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: explain causal chain |
| 7 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns correlation report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/change-correlation`
- **Method:** POST
- **Request body:**
  ```json
  {
    "behaviorShift": "rejection_rate_spike",
    "metric": "eur_rejection_pct",
    "shiftOnset": "2025-07-25T14:05:00Z",
    "magnitude": "+28%",
    "affectedScope": { "region": "EMEA", "pairs": ["EUR/GBP"] },
    "correlationWindow": "2h",
    "correlationId": "corr-chg-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `getRecentChanges(window)` | onset - window | change events |
| 2 | `correlateChangeToOutcome()` | changes + shift | correlation scores |
| 3 | `getChangeGraph(entity)` | top correlated entity | dependency path |

Steps 1-2 are sequential; step 3 depends on step 2 output.

---

## 5. LLM Node Configuration

### Causal Analyzer (Node 6)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a change correlation analyst for an FX platform. Given: behavior shift details, recent changes (ranked by correlation score), dependency graph, and similar prior incidents — produce a causal hypothesis. Include: (1) most probable cause with confidence, (2) causal chain (change → service → metric), (3) alternative hypotheses, (4) reference to similar past incidents if found. Output: `rejection↑14:05 ← rule pkg 7.14 @14:01 → EUR/GBP +28% → book B17` style summary + detailed CorrelationReport JSON."
- **Temperature:** 0.2

---

## 6. HITL Gate

**Not applicable.** This agent is Risk L (advisory only).

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Change service unavailable | Report "unable to correlate"; suggest manual audit log check |
| No changes in window | Return "No changes found in correlation window" |
| Graph unavailable | Skip dependency path; note limitation |
| LLM malformed output | Retry once; return raw correlation data |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| CHG-EVAL-01 | Integration | Rejection spike at 14:05 | Correlates rule v7.14 at 14:01 |
| CHG-EVAL-02 | Deploy | Latency after K8s deploy | Service correlation |
| CHG-EVAL-03 | No changes | Empty window | "No correlated changes" |
| CHG-EVAL-04 | Multiple | Multiple changes | Ranked by probability |
| CHG-EVAL-05 | Memory | Known pattern | References prior incident |
| CHG-EVAL-06 | Schema | Schema change → consumer impact | Explains chain |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Change Discovery | Node 2 (getRecentChanges) |
| Rq2: Causal Correlation | Nodes 3-4 (correlate + graph) + Node 6 (LLM) |
| Rq3: Pattern Recognition | Node 5 (similar incidents) |
