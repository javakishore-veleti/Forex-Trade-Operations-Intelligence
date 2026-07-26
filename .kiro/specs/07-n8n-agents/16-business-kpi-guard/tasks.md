# Tasks — Business KPI Guard Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/specialized-business-kpi-guard.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Business KPI Guard"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/business-kpi-guard`, receives AnomalyEnvelope with `kpi`, `region`, `currentValue`, `baselineValue`, `deviationPct`
- [x] Task 3: Add Get Business KPIs node — HTTP GET to kpi-mcp `getBusinessKpis(region)`
- [x] Task 4: Add Get Seasonal Baseline node — HTTP GET to kpi-mcp `getSeasonalBaseline(kpi)` (parallel with node 3)
- [x] Task 5: Add Get Reject Breakdown node — HTTP GET to kpi-mcp `getRejectBreakdown()` (parallel)
- [x] Task 6: Add Root Cause Analyzer LLM node — Opus-class, produces KPIAnomalyReport with confirmation, scope, cause, action
- [x] Task 7: Add Respond to Webhook node — returns KPIAnomalyReport
- [x] Task 8: Wire all connections: Trigger → parallel (KPIs, Baseline, Rejects) → LLM → Response
- [x] Task 9: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/specialized-business-kpi-guard.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] No HITL gate present (Risk L agent)
- [x] All 3 data fetch nodes configured as parallel
- [x] Connections reference existing node names
