# Design — Service Genome Agent

## 1. Overview

The Service Genome Agent maintains service knowledge profiles and predicts
fragility for change planning. Risk L — read-only profiling and analysis.

**Trigger mechanism:** Webhook (from Supervisor Agent or scheduled refresh).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Get Service Profile] → [Get Dependencies]
                                                    │
                                                    ▼
                                     [Predict Fragility]
                                                    │
                                                    ▼
                                     [Find Change Consumers]
                                                    │
                                                    ▼
                                     [LLM: Genome Analyzer]
                                                    │
                                                    ▼
                                     [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives `{ service, queryType, correlationId }` |
| 2 | Get Service Profile | `n8n-nodes-base.httpRequest` | GET `getServiceProfile(svc)` |
| 3 | Get Dependencies | `n8n-nodes-base.httpRequest` | GET `getDependencies(svc)` |
| 4 | Predict Fragility | `n8n-nodes-base.httpRequest` | GET `predictFragility(svc)` — Python sidecar |
| 5 | Find Change Consumers | `n8n-nodes-base.httpRequest` | GET `findConsumersOfChange()` |
| 6 | Genome Analyzer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: synthesis + explanation |
| 7 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns ServiceGenomeEnvelope |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/service-genome`
- **Method:** POST
- **Request body:**
  ```json
  {
    "service": "risk-calculation-service",
    "queryType": "FRAGILITY",
    "correlationId": "corr-genome-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `getServiceProfile` | service | runtime profile |
| 2 | `getDependencies` | service | dependency graph |
| 3 | `predictFragility` | service | fragility score + factors |
| 4 | `findConsumersOfChange` | service | consumer list |

Steps 2-4 can execute in parallel after step 1.

---

## 5. LLM Node Configuration

### Genome Analyzer (Node 6)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a service architecture analyst for an FX operations platform. Given: (1) service profile, (2) dependency map, (3) fragility score with factors, (4) change consumers — produce a comprehensive genome report. For FRAGILITY queries: explain why the score is what it is, identify the primary risk drivers, recommend safeguards. For PROFILE queries: summarize the service's role and characteristics. For CHANGE_IMPACT queries: describe who is affected and how. Output JSON: `{ service, profile, fragilityScore, fragilityDrivers[], consumers[], recommendations[], narrative }`."
- **Temperature:** 0.1

---

## 6. HITL Gate

**Not applicable.** This agent is Risk L (read-only profiling). No actions are proposed or executed.

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Service not found in registry | Return "Service not registered" |
| Graph store unavailable | Return partial profile without deps |
| Python sidecar timeout | Return profile without fragility; note gap |
| No consumers found | Note service has no known consumers |
| LLM malformed output | Retry once; fallback to raw data |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| GENOME-EVAL-01 | Profile | risk-calculation-service | Full profile |
| GENOME-EVAL-02 | Fragility | Most fragile service | Ranked list |
| GENOME-EVAL-03 | Consumers | trade-lifecycle API | Consumer map |
| GENOME-EVAL-04 | Change impact | enrichment-svc change | Impact report |
| GENOME-EVAL-05 | Unknown | non-existent-svc | "Not found" |
| GENOME-EVAL-06 | Independent | No-dependency service | Profile |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Service Profiling | Nodes 2-3 (profile + deps) |
| Rq2: Fragility Prediction | Node 4 (sidecar) + Node 6 (LLM explain) |
| Rq3: Change Consumers | Node 5 (consumers) + Node 6 (LLM map) |
