# Tasks — Trade Amendment Ripple Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/trade-amendment-ripple.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Trade Amendment Ripple Agent"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/trade-amendment-ripple`, receives `tradeId`, `eventType`, `correlationId`
- [ ] Task 3: Add Find Downstream Effects node — HTTP GET to graph-mcp `findDownstreamEffects(tradeId)`
- [ ] Task 4: Add Check Risk Recalc node — HTTP GET to risk-calculation-mcp `checkRiskRecalcTriggered()`
- [ ] Task 5: Add Check Settlement node — HTTP GET to settlement-mcp `checkSettlementWithdrawn()`
- [ ] Task 6: Add Check Report node — HTTP GET to reporting-mcp `checkReportAmended()`
- [ ] Task 7: Add Ripple Analyzer LLM node — Opus-class, identifies gaps and produces corrective recommendations
- [ ] Task 8: Add Gap Check IF node — branches if gaps detected
- [ ] Task 9: Add Correction Approval Wait node — HITL gate, 4h timeout
- [ ] Task 10: Add Approval Handler IF node — branches approved/denied
- [ ] Task 11: Add Request Missing Recalc node — HTTP POST to risk-calculation-mcp `requestMissingRecalc()`
- [ ] Task 12: Add Respond to Webhook node — returns ripple report
- [ ] Task 13: Wire connections: Trigger → Effects → parallel (Risk, Settlement, Report) → LLM → Gate → Response
- [ ] Task 14: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/trade-amendment-ripple.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] HITL gate blocks corrective action execution
- [ ] Parallel checks execute correctly
- [ ] Connections reference existing node names
