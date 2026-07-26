# Tasks — Retry-Storm & Backpressure Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/retry-storm.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Retry Storm Backpressure Agent"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/retry-storm`, receives `service`, `alertType`, `correlationId`
- [ ] Task 3: Add Get Retry Amplification node — HTTP GET to observability-mcp `getRetryAmplification()`
- [ ] Task 4: Add Get Open Breakers node — HTTP GET to observability-mcp `getOpenBreakers()`
- [ ] Task 5: Add Get Cascade Path node — HTTP GET to graph-mcp `getCascadePath(service)`
- [ ] Task 6: Add Root Cause Analyzer LLM node — Opus-class, distinguishes root from symptom
- [ ] Task 7: Add Storm Check IF node — branches if storm confirmed
- [ ] Task 8: Add Backpressure Approval Wait node — HITL gate, 10min timeout
- [ ] Task 9: Add Approval Handler IF node — branches approved/denied
- [ ] Task 10: Add Apply Backpressure node — HTTP POST to routing-mcp `applyBackpressure()` or `tripBreaker()`
- [ ] Task 11: Add Respond to Webhook node — returns storm report
- [ ] Task 12: Wire all connections including parallel metric fetches and HITL branch
- [ ] Task 13: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/retry-storm.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] HITL gate blocks backpressure execution
- [ ] All node types use valid n8n type identifiers
- [ ] Connections reference existing node names
