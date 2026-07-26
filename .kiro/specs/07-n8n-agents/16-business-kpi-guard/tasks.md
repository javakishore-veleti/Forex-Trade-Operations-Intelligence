# Tasks — Business KPI Guard Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/business-kpi-guard.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Business KPI Guard"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/business-kpi-guard`, receives AnomalyEnvelope with `kpi`, `region`, `currentValue`, `baselineValue`, `deviationPct`
- [ ] Task 3: Add Get Business KPIs node — HTTP GET to kpi-mcp `getBusinessKpis(region)`
- [ ] Task 4: Add Get Seasonal Baseline node — HTTP GET to kpi-mcp `getSeasonalBaseline(kpi)` (parallel with node 3)
- [ ] Task 5: Add Get Reject Breakdown node — HTTP GET to kpi-mcp `getRejectBreakdown()` (parallel)
- [ ] Task 6: Add Root Cause Analyzer LLM node — Opus-class, produces KPIAnomalyReport with confirmation, scope, cause, action
- [ ] Task 7: Add Respond to Webhook node — returns KPIAnomalyReport
- [ ] Task 8: Wire all connections: Trigger → parallel (KPIs, Baseline, Rejects) → LLM → Response
- [ ] Task 9: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/business-kpi-guard.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] No HITL gate present (Risk L agent)
- [ ] All 3 data fetch nodes configured as parallel
- [ ] Connections reference existing node names
