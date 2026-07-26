# Tasks — Runtime Change Correlation Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/specialized-change-correlation.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Change Correlation Agent"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/change-correlation`, receives behavior shift envelope with `shiftOnset`, `magnitude`, `affectedScope`
- [x] Task 3: Add Get Recent Changes node — HTTP GET to change-mcp `getRecentChanges(window)` for all changes in correlation window
- [x] Task 4: Add Correlate Change node — HTTP GET to change-mcp `correlateChangeToOutcome()` for correlation scores
- [x] Task 5: Add Get Change Graph node — HTTP GET to change-mcp `getChangeGraph(entity)` for top correlated entity
- [x] Task 6: Add Similar Incident Retrieval node — HTTP POST to vector-search endpoint for prior patterns
- [x] Task 7: Add Causal Analyzer LLM node — Opus-class, produces CorrelationReport with causal hypothesis, confidence, alternatives
- [x] Task 8: Add Respond to Webhook node — returns CorrelationReport
- [x] Task 9: Wire all connections: Trigger → Changes → Correlate → Graph → Similar → LLM → Response
- [x] Task 10: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/specialized-change-correlation.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] No HITL gate present (Risk L agent)
- [x] Sequential dependency: Graph depends on Correlate output
- [x] Connections reference existing node names
