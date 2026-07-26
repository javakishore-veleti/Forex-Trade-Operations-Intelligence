# Design — Currency-Pair Rule-Coverage Agent

## 1. Overview

The Rule-Coverage Agent monitors rule coverage across currency pairs, detects
gaps and excessive fallback reliance, and explains the business risk. It is a
read-only advisory agent (Risk L) with no HITL gate.

**Trigger mechanism:** Webhook (sidecar anomaly envelope OR Supervisor routing).

---

## 2. n8n Workflow Structure

```
[Webhook Trigger] → [Fetch Coverage Matrix] → [Fetch Fallback Rate] → [Simulate Gap]
                                                                            │
                                                                            ▼
                                                                [LLM: Gap Analyzer]
                                                                            │
                                                                            ▼
                                                                [Webhook Response]
```

### Node-by-Node Description

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Webhook Trigger | `n8n-nodes-base.webhook` | Receives anomaly envelope or query |
| 2 | Fetch Coverage Matrix | `n8n-nodes-base.httpRequest` | GET `getRuleCoverageMatrix()` |
| 3 | Fetch Fallback Rate | `n8n-nodes-base.httpRequest` | GET `getFallbackFiringRate(pair)` |
| 4 | Fetch Uncovered Pairs | `n8n-nodes-base.httpRequest` | GET `getUncoveredPairs()` |
| 5 | Simulate Gap | `n8n-nodes-base.httpRequest` | GET `simulateRuleGap(pair)` |
| 6 | Gap Analyzer (LLM) | `@n8n/n8n-nodes-langchain.agent` | Opus: explain gap + recommend |
| 7 | Webhook Response | `n8n-nodes-base.respondToWebhook` | Returns coverage report |

---

## 3. Trigger Configuration

- **Type:** Webhook (POST)
- **Path:** `/webhook/rule-coverage`
- **Method:** POST
- **Request body:**
  ```json
  {
    "triggerType": "ANOMALY|QUERY",
    "pair": "USD/TRY",
    "fallbackRate": 94.2,
    "baselineRate": 15.0,
    "correlationId": "corr-cov-001"
  }
  ```

---

## 4. MCP Tool Call Order

| Step | Tool | Input | Output |
|------|------|-------|--------|
| 1 | `getRuleCoverageMatrix()` | — | Full pair→rule mapping |
| 2 | `getFallbackFiringRate(pair)` | pair | Current rate + baseline |
| 3 | `getUncoveredPairs()` | — | List of pairs with no specific rules |
| 4 | `simulateRuleGap(pair)` | pair | Fallback vs expected behavior diff |

Steps 2-3 are parallel.

---

## 5. LLM Node Configuration

### Gap Analyzer (Node 6)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt:** "You are a rule coverage analyst for an FX platform. Given coverage matrix, fallback firing rates, uncovered pairs, and gap simulation results — explain: (1) which pairs are at risk, (2) what business impact the gap creates (mispricing, unintended exposure), (3) what type of rule is needed (without writing the rule). Rank by materiality. Never invent data. Output as CoverageReport envelope."
- **Temperature:** 0.1

---

## 6. HITL Gate

**Not applicable.** This agent is Risk L (advisory only).

---

## 7. Error Handling

| Failure Mode | Handling |
|---|---|
| Coverage matrix unavailable | Report error; suggest manual rules console check |
| Simulation fails | Skip simulation; note gap unvalidated |
| Unknown pair | Return "Pair not found in coverage matrix" |
| LLM malformed output | Retry once; fallback to raw matrix data |

---

## 8. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| COV-EVAL-01 | Query | "Show uncovered pairs" | Ranked list |
| COV-EVAL-02 | Anomaly | High fallback rate for USD/TRY | Gap explanation + risk |
| COV-EVAL-03 | New pair | New pair no rules | Flags uncovered |
| COV-EVAL-04 | Full coverage | All pairs covered | "No gaps" message |
| COV-EVAL-05 | Specific | "Is EUR/GBP covered?" | Shows specific rules |

---

## 9. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Coverage Matrix | Nodes 2 + 4 (fetch matrix + uncovered) |
| Rq2: Fallback Monitoring | Nodes 1 (trigger) + 3 (fallback rate) |
| Rq3: Gap Explanation | Nodes 5 (simulate) + 6 (LLM analysis) |
