# Tasks — Counterparty Exposure Narrative Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/specialized-counterparty-exposure.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Counterparty Exposure Agent"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/counterparty-exposure`, receives `counterpartyId`, `question`, `correlationId`
- [x] Task 3: Add Get Exposure node — HTTP GET to exposure-mcp `getCounterpartyExposure(cp)`
- [x] Task 4: Add Get Limits node — HTTP GET to exposure-mcp `getLimits(cp)` (parallel with node 3)
- [x] Task 5: Add Get Concentration node — HTTP GET to exposure-mcp `getConcentration(cp)` (parallel)
- [x] Task 6: Add Get Collateral node — HTTP GET to exposure-mcp `getCollateral(cp)` (parallel)
- [x] Task 7: Add Get Prior Day Exposure node — HTTP GET to exposure-mcp `getPriorDayExposure(cp)` (parallel)
- [x] Task 8: Add Narrative Synthesizer LLM node — Opus-class, produces ExposureStory with position, limits, change, concentration, collateral
- [x] Task 9: Add Respond to Webhook node — returns ExposureStory to caller
- [x] Task 10: Wire all connections: Trigger → parallel (5 data nodes) → merge → LLM → Response
- [x] Task 11: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/specialized-counterparty-exposure.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] All 5 data fetch nodes configured as parallel execution
- [x] No HITL gate present (Risk L agent)
- [x] Connections reference existing node names
