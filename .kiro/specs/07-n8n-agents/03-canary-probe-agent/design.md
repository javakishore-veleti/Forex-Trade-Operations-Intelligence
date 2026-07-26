# Design — Synthetic Business-Probe (Canary Trade) Agent

## 1. Overview

The Canary Probe Agent continuously validates business liveness by injecting synthetic trades into the real pipeline on a schedule, tracing their progress through lifecycle stages, asserting stage completion within SLA, and triggering business degradation alerts when stages are stuck. Unlike infrastructure health checks, this proves the pipeline is actually processing trades correctly.

**Trigger mechanism:** Schedule (cron interval, default every 5 minutes).

---

## 2. n8n Workflow Structure

```
[Schedule Trigger (every 5 min)] → [Get Active Regions (Config)] → [Loop: Per Region]
                                                                          │
                                                                          ▼
                                                          [Inject Synthetic Trade (MCP)]
                                                                          │
                                                                          ▼
                                                          [Verify Synthetic Flag (getTrade MCP)]
                                                                          │
                                                                          ▼
                                                          [Critical Flag Check (IF)]
                                                                    │           │
                                                             missing flag    flag OK
                                                                    │           │
                                                          [Critical Alert]      │
                                                                                ▼
                                                          [Wait (SLA window)]
                                                                                │
                                                                                ▼
                                                          [Trace Progress (MCP)]
                                                                                │
                                                                                ▼
                                                          [SLA Assertion (Code Node)]
                                                                    │           │
                                                              stuck          passed
                                                                    │           │
                                                                    ▼           ▼
                                                  [Assert Expected Lifecycle]  [Log Success]
                                                                    │
                                                                    ▼
                                                  [LLM: Diagnose Stuck Stage]
                                                                    │
                                                                    ▼
                                                  [Open Business Degradation (HITL Gated)]
                                                                    │
                                                                    ▼
                                                  [Wait for Approval]
                                                                    │
                                                                    ▼
                                                  [Execute or Cancel Alert]
```

### Node-by-Node Description:

| # | Node Name | Type | Purpose |
|---|-----------|------|---------|
| 1 | Schedule Trigger | `n8n-nodes-base.scheduleTrigger` | Fires every 5 minutes (configurable) |
| 2 | Get Active Regions | `n8n-nodes-base.set` | Sets region list (APAC, EMEA, AMER) and currency pairs |
| 3 | Loop Regions | `n8n-nodes-base.splitInBatches` | Iterates over each region |
| 4 | Inject Synthetic Trade | `n8n-nodes-base.httpRequest` | MCP: `injectSyntheticTrade` on trade-ingest-service |
| 5 | Verify Synthetic Flag | `n8n-nodes-base.httpRequest` | MCP: `getTrade` to confirm `synthetic=true` |
| 6 | Critical Flag Check | `n8n-nodes-base.if` | Checks if synthetic flag is present |
| 7 | Critical Alert | `n8n-nodes-base.httpRequest` | Fires critical alert if flag missing; halts |
| 8 | Wait for SLA | `n8n-nodes-base.wait` | Waits configurable SLA window (e.g., 120s) |
| 9 | Trace Progress | `n8n-nodes-base.httpRequest` | MCP: `traceSyntheticProgress` for current stage |
| 10 | SLA Assertion | `n8n-nodes-base.code` | Deterministic: compares elapsed time vs SLA thresholds |
| 11 | Assert Expected Lifecycle | `n8n-nodes-base.httpRequest` | MCP: `assertExpectedLifecycle` confirms stuck |
| 12 | Diagnose Stuck Stage (LLM) | `@n8n/n8n-nodes-langchain.agent` | Deep reasoning: why is stage stuck? |
| 13 | Open Business Degradation | `n8n-nodes-base.httpRequest` | MCP: `openBusinessDegradation` (Risk M, gated) |
| 14 | Wait for Approval | `n8n-nodes-base.wait` | HITL gate: pause until human approves alert |
| 15 | Log Success | `n8n-nodes-base.set` | Records successful per-stage latencies |

---

## 3. Trigger Configuration

- **Type:** Schedule Trigger (cron)
- **Interval:** Every 5 minutes (configurable via workflow settings)
- **No external trigger** — this is a self-initiating probe

---

## 4. MCP Tool Calls

| Step | Tool | Endpoint | Input | Output |
|------|------|----------|-------|--------|
| 1 | `injectSyntheticTrade` | `http://trade-lifecycle-service:8081/mcp/injectSyntheticTrade` | `{ "region": "APAC", "currencyPair": "EUR/USD", "synthetic": true }` | `{ "tradeId": "FX-CANARY-APAC-1721900000", "status": "CAPTURED" }` |
| 2 | `getTrade` | `http://trade-lifecycle-service:8081/mcp/getTrade` | `{ "tradeId": "FX-CANARY-APAC-1721900000" }` | Trade state including `synthetic` flag |
| 3 | `traceSyntheticProgress` | `http://trade-lifecycle-service:8081/mcp/traceSyntheticProgress` | `{ "tradeId": "FX-CANARY-APAC-1721900000" }` | Current stage, timestamps per stage |
| 4 | `assertExpectedLifecycle` | `http://trade-lifecycle-service:8081/mcp/assertExpectedLifecycle` | `{ "tradeId": "...", "expectedStage": "ENRICHED" }` | Assertion result: pass/fail with details |
| 5 | `openBusinessDegradation` | `http://eod-processing-service:8084/mcp/openBusinessDegradation` | `{ "region": "EMEA", "stuckStage": "ENRICHED", "tradeId": "...", "elapsedSeconds": 180, "slaSecs": 60 }` | Alert ID (Risk M, requires HITL) |

---

## 5. LLM Node Configuration

### Diagnose Stuck Stage (Node 12)
- **Model tier:** Deep reasoning (Opus-class)
- **System prompt summary:** "You are a pipeline health diagnostician. Given a stuck synthetic trade (region, stuck stage, elapsed time, SLA), the assertion failure details, and the trade's progress history, explain why this stage may be stuck. Base your analysis only on the provided tool data. Suggest which service or condition is causing the block."
- **Output structure:**
  ```json
  {
    "diagnosis": "The enrichment-service appears to be unresponsive for EMEA trades. The synthetic trade was captured at 10:00:00 but has not progressed past VALIDATED for 3 minutes (SLA: 60s).",
    "probableService": "enrichment-service",
    "confidence": "medium",
    "remediationHint": "Check enrichment-service health; may require restart or scaling"
  }
  ```
- **Temperature:** 0.2
- **Invocation condition:** ONLY when stuck stage is detected (happy path uses no LLM)

---

## 6. Memory/Session

- **Rolling probe memory (CACHE):** Redis stores per-region probe results for the last 24 hours:
  - Probe timestamp, region, all stage latencies, pass/fail
  - Used for trend detection (e.g., "EMEA enrichment latency increasing over last 2 hours")
- **No multi-turn conversation memory** — each probe run is independent
- **Idempotent alerting:** Cache tracks active degradation alerts per region+stage to prevent duplicates

---

## 7. HITL Gate

- **Placement:** Before executing `openBusinessDegradation` (Node 14: Wait for Approval)
- **Condition:** Always required — `openBusinessDegradation` is Risk M
- **Wait node configuration:**
  - Resume on webhook: `POST /webhook/canary-approval/{executionId}`
  - Timeout: 1 hour (configurable)
- **Approval payload:**
  ```json
  {
    "executionId": "exec-456",
    "decision": "APPROVED",
    "approverUserId": "user-ops-02",
    "approvalReference": "apr-ref-789",
    "timestamp": "2025-07-25T10:35:00Z"
  }
  ```
- **On denial:** Log that degradation was not opened; retain probe data for audit

---

## 8. Error Handling

| Failure Mode | Handling |
|---|---|
| `injectSyntheticTrade` returns SANDBOX_NOT_ENABLED | Halt all injections; raise critical alert |
| Synthetic flag missing on read-back | Raise critical alert; halt further injections for that region |
| `traceSyntheticProgress` fails | Retry once after 30s; if still fails, mark region as "probe-unavailable" |
| LLM diagnosis fails | Proceed with opening degradation alert using only deterministic data (no diagnosis text) |
| Schedule fires but n8n is overloaded | Queue execution; n8n handles this natively |
| Duplicate degradation already active | Skip alert opening (idempotent); log "duplicate skipped" |

---

## 9. Testing Strategy

| Scenario ID | Test Type | Input | Expected Outcome |
|---|---|---|---|
| CAN-EVAL-01 | Happy path | All stages complete within SLA for APAC | Success logged; no alert; per-stage latencies recorded |
| CAN-EVAL-02 | Stuck stage | FX-CANARY-EMEA stuck at ENRICHED for 3x SLA | StuckStage detected; assertExpectedLifecycle confirms; degradation proposed (HITL) |
| CAN-EVAL-03 | Sandbox disabled | injectSyntheticTrade returns SANDBOX_NOT_ENABLED | Critical alert raised; no injection |
| CAN-EVAL-04 | Missing flag | getTrade read-back lacks `synthetic=true` | Critical alert; halt injections |
| CAN-EVAL-05 | Duplicate alert | EMEA ENRICHED already has active alert | Does NOT open duplicate; logs idempotent skip |
| CAN-EVAL-06 | Multi-region | APAC pass, EMEA stuck, AMER pass | Per-region status; only EMEA triggers degradation |

---

## 10. Requirement → Design Traceability

| Requirement | Design Element |
|---|---|
| Rq1: Synthetic Trade Injection | Node 4 (injectSyntheticTrade MCP call with FX-CANARY-* ID) |
| Rq2: Sandbox Guardrail | Nodes 5 + 6 (read-back verification of synthetic flag) |
| Rq3: Stage Progress Tracing | Nodes 9 + 10 (traceSyntheticProgress + deterministic SLA assertion) |
| Rq4: Business Degradation Detection & Alerting | Nodes 11-14 (assert → diagnose → open alert → HITL) |
| Rq5: Model Tier Allocation | Deterministic for happy path; LLM only on degradation (Node 12) |
| Rq6: Probe Schedule Configuration | Node 1 (Schedule Trigger) + Node 2 (externalized config) |
