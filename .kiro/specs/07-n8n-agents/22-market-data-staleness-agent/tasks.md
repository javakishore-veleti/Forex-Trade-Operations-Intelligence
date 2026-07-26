# Tasks — Market-Data Feed Staleness Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/market-data-staleness.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Market Data Feed Staleness Agent"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/market-data-staleness`, receives `pairs`, `region`, `correlationId`
- [ ] Task 3: Add Get Stale Pairs node — HTTP GET to market-data-mcp `getStalePairs()`
- [ ] Task 4: Add Detect Crossed Quotes node — HTTP GET to market-data-mcp `detectCrossedQuote()`
- [ ] Task 5: Add Get Downstream Dependencies node — HTTP GET to risk-calculation-mcp `getDownstreamRiskDependency(pair)` for each affected pair
- [ ] Task 6: Add Impact Analyzer LLM node — Opus-class, assesses impact and recommends block
- [ ] Task 7: Add Block Check IF node — branches if block is recommended
- [ ] Task 8: Add Block Approval Wait node — HITL gate for risk calc blocking, 15min timeout
- [ ] Task 9: Add Approval Handler IF node — branches approved/denied
- [ ] Task 10: Add Block Risk Calc node — HTTP POST to risk-calculation-mcp `blockRiskCalc(pair)`
- [ ] Task 11: Add Respond to Webhook node — returns staleness report
- [ ] Task 12: Wire all connections including parallel staleness/crossed checks and HITL branch
- [ ] Task 13: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/market-data-staleness.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] HITL gate blocks risk calc blocking execution
- [ ] All node types use valid n8n type identifiers
- [ ] Connections reference existing node names
