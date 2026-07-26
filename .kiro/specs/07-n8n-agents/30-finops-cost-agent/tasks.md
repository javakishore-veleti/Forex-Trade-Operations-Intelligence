# Tasks — FinOps Cost-Anomaly Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/finops-cost.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "FinOps Cost Anomaly Agent"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/finops-cost`, receives `timeRange`, `threshold`, `correlationId`
- [x] Task 3: Add Get Cost By Service node — HTTP GET to finops-mcp `getCostByService()`
- [x] Task 4: Add Correlate Cost to Deploy node — HTTP GET to finops-mcp `correlateCostToDeploy()`
- [x] Task 5: Add Get Idle Capacity node — HTTP GET to finops-mcp `getIdleCapacity()`
- [x] Task 6: Add Cost Analyzer LLM node — Opus-class, determines root cause and produces recommendations
- [x] Task 7: Add Action Check IF node — branches if actionable savings identified
- [x] Task 8: Add Propose Rightsizing node — HTTP POST to finops-mcp `proposeRightsizing()`
- [x] Task 9: Add Scale Approval Wait node — HITL gate for scale-down, 24h timeout
- [x] Task 10: Add Approval Handler IF node — branches approved/denied
- [x] Task 11: Add Apply Scale Down node — HTTP POST to finops-mcp `applyScaleDown()`
- [x] Task 12: Add Respond to Webhook node — returns cost report
- [x] Task 13: Wire all connections including parallel fetches and HITL branch
- [x] Task 14: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/finops-cost.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] HITL gate blocks scale-down execution
- [x] All node types use valid n8n type identifiers
- [x] Connections reference existing node names
