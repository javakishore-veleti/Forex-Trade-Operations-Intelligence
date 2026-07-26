# Tasks — Retry-Storm & Backpressure Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/retry-storm.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Retry Storm Backpressure Agent"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/retry-storm`, receives `service`, `alertType`, `correlationId`
- [x] Task 3: Add Get Retry Amplification node — HTTP GET to observability-mcp `getRetryAmplification()`
- [x] Task 4: Add Get Open Breakers node — HTTP GET to observability-mcp `getOpenBreakers()`
- [x] Task 5: Add Get Cascade Path node — HTTP GET to graph-mcp `getCascadePath(service)`
- [x] Task 6: Add Root Cause Analyzer LLM node — Opus-class, distinguishes root from symptom
- [x] Task 7: Add Storm Check IF node — branches if storm confirmed
- [x] Task 8: Add Backpressure Approval Wait node — HITL gate, 10min timeout
- [x] Task 9: Add Approval Handler IF node — branches approved/denied
- [x] Task 10: Add Apply Backpressure node — HTTP POST to routing-mcp `applyBackpressure()` or `tripBreaker()`
- [x] Task 11: Add Respond to Webhook node — returns storm report
- [x] Task 12: Wire all connections including parallel metric fetches and HITL branch
- [x] Task 13: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/retry-storm.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] HITL gate blocks backpressure execution
- [x] All node types use valid n8n type identifiers
- [x] Connections reference existing node names
