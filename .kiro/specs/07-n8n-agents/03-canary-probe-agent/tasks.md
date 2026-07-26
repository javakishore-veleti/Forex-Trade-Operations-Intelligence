# Tasks — Canary Probe Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/specialized-canary-probe.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name`, `settings`, `active`, `id`)
- [x] Task 2: Add Schedule Trigger node — fires every 5 minutes (configurable cron)
- [x] Task 3: Add Get Active Regions Set node — defines region list (APAC, EMEA, AMER), currency pair rotation, SLA thresholds
- [x] Task 4: Add Split In Batches node — iterates over each active region
- [x] Task 5: Add Inject Synthetic Trade HTTP Request node — POST to `http://trade-lifecycle-service:8081/mcp/injectSyntheticTrade`
- [x] Task 6: Add Verify Synthetic Flag HTTP Request node — POST to `http://trade-lifecycle-service:8081/mcp/getTrade` for read-back
- [x] Task 7: Add Critical Flag Check IF node — checks `synthetic=true` on read-back; branches to critical alert if missing
- [x] Task 8: Add Critical Alert HTTP node — sends critical alert when sandbox guardrail violated
- [x] Task 9: Add Wait for SLA node — pauses execution for configurable SLA window (120s default)
- [x] Task 10: Add Trace Progress HTTP Request node — POST to `http://trade-lifecycle-service:8081/mcp/traceSyntheticProgress`
- [x] Task 11: Add SLA Assertion Code node — deterministic comparison of elapsed time vs SLA thresholds (no LLM)
- [x] Task 12: Add Assert Expected Lifecycle HTTP Request node — POST to `http://trade-lifecycle-service:8081/mcp/assertExpectedLifecycle`
- [x] Task 13: Add Diagnose Stuck Stage LLM node — deep reasoning model for degradation diagnosis (only invoked on failure)
- [x] Task 14: Add Open Business Degradation HTTP Request node — POST to `http://eod-processing-service:8084/mcp/openBusinessDegradation` (Risk M)
- [x] Task 15: Add Wait for Approval node — HITL gate before degradation alert is committed
- [x] Task 16: Add Log Success Set node — records successful probe with per-stage latencies
- [x] Task 17: Wire all connections between nodes (main path + error branches + loop back)
- [x] Task 18: Set realistic node positions (x, y coordinates) for visual layout

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/specialized-canary-probe.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] All node types use valid n8n type identifiers
- [x] Schedule trigger is properly configured with cron expression
- [x] Connections reference existing node names
- [x] HITL Wait node present before openBusinessDegradation execution
