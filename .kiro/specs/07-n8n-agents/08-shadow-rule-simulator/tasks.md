# Tasks — Shadow Rule Simulator Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/shadow-rule-simulator.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Shadow Rule Simulator"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/shadow-rule-simulator`, receives `ruleDescription`, `replayWindow`, `requestedBy`
- [ ] Task 3: Add DRL Corpus Retrieval node — HTTP POST to vector-search endpoint with NL description as query
- [ ] Task 4: Add NL→DRL Generator LLM node — Opus-class, generates DRL from NL + corpus examples
- [ ] Task 5: Add Validate DRL node — HTTP POST to shadow-rules-mcp for parse/compile validation
- [ ] Task 6: Add Validation Check IF node — branches on valid/invalid DRL
- [ ] Task 7: Add Reflection Fix LLM node — Opus-class, corrects DRL based on validation error message
- [ ] Task 8: Add Re-validate node — HTTP POST to shadow-rules-mcp for re-validation
- [ ] Task 9: Add Retry Counter IF node — limits reflection loop to max 3 iterations
- [ ] Task 10: Add Load Shadow Rule node — HTTP POST to shadow-rules-mcp `loadShadowRule(drl)`
- [ ] Task 11: Add Replay Historical Events node — HTTP POST to shadow-rules-mcp `replayHistoricalEvents(window)`
- [ ] Task 12: Add Read Shadow Results node — HTTP GET from shadow-rules-mcp `readShadowRiskResults()`
- [ ] Task 13: Add Diff Against Production node — HTTP GET from shadow-rules-mcp `diffAgainstProduction()`
- [ ] Task 14: Add Impact Explainer LLM node — Opus-class, explains diff in business terms
- [ ] Task 15: Add HITL Gate Wait node — pauses for deploy approval, resumes on webhook
- [ ] Task 16: Add Approval Check IF node — branches on APPROVED vs DENIED
- [ ] Task 17: Add Hand Off to Deploy node — HTTP POST to deployment pipeline notification
- [ ] Task 18: Add Archive Results node — HTTP POST to store shadow results for reference
- [ ] Task 19: Add Respond to Webhook node — returns impact report
- [ ] Task 20: Wire all connections including reflection loop and HITL branching
- [ ] Task 21: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/shadow-rule-simulator.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] Reflection loop correctly limited to 3 iterations
- [ ] HITL gate correctly blocks production deployment
- [ ] Connections reference existing node names
