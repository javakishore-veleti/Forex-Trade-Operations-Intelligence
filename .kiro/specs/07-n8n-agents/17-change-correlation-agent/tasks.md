# Tasks — Runtime Change Correlation Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/change-correlation.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Change Correlation Agent"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/change-correlation`, receives behavior shift envelope with `shiftOnset`, `magnitude`, `affectedScope`
- [ ] Task 3: Add Get Recent Changes node — HTTP GET to change-mcp `getRecentChanges(window)` for all changes in correlation window
- [ ] Task 4: Add Correlate Change node — HTTP GET to change-mcp `correlateChangeToOutcome()` for correlation scores
- [ ] Task 5: Add Get Change Graph node — HTTP GET to change-mcp `getChangeGraph(entity)` for top correlated entity
- [ ] Task 6: Add Similar Incident Retrieval node — HTTP POST to vector-search endpoint for prior patterns
- [ ] Task 7: Add Causal Analyzer LLM node — Opus-class, produces CorrelationReport with causal hypothesis, confidence, alternatives
- [ ] Task 8: Add Respond to Webhook node — returns CorrelationReport
- [ ] Task 9: Wire all connections: Trigger → Changes → Correlate → Graph → Similar → LLM → Response
- [ ] Task 10: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/change-correlation.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] No HITL gate present (Risk L agent)
- [ ] Sequential dependency: Graph depends on Correlate output
- [ ] Connections reference existing node names
