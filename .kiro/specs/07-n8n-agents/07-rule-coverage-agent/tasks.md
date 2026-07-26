# Tasks — Currency-Pair Rule-Coverage Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/rule-coverage.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Rule Coverage Agent"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/rule-coverage`, receives anomaly envelope or query with `pair`, `fallbackRate`, `triggerType`
- [ ] Task 3: Add Fetch Coverage Matrix node — HTTP GET to rules-engine-mcp `getRuleCoverageMatrix()`
- [ ] Task 4: Add Fetch Fallback Rate node — HTTP GET to rules-engine-mcp `getFallbackFiringRate(pair)`
- [ ] Task 5: Add Fetch Uncovered Pairs node — HTTP GET to rules-engine-mcp `getUncoveredPairs()`
- [ ] Task 6: Add Simulate Gap node — HTTP GET to rules-engine-mcp `simulateRuleGap(pair)` for fallback appropriateness
- [ ] Task 7: Add Gap Analyzer LLM node — Opus-class, produces CoverageReport envelope with ranked gaps, business risk, and rule type recommendations
- [ ] Task 8: Add Respond to Webhook node — returns CoverageReport to caller
- [ ] Task 9: Wire all connections: Trigger → parallel (Matrix, Fallback, Uncovered) → Simulate → LLM → Response
- [ ] Task 10: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/rule-coverage.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] All node types use valid n8n type identifiers
- [ ] Connections reference existing node names
- [ ] No HITL gate present (Risk L agent)
