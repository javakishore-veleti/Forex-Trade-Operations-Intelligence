# Tasks — Shadow Rule Simulator Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/specialized-shadow-rule-simulator.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Shadow Rule Simulator"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/shadow-rule-simulator`, receives `ruleDescription`, `replayWindow`, `requestedBy`
- [x] Task 3: Add DRL Corpus Retrieval node — HTTP POST to vector-search endpoint with NL description as query
- [x] Task 4: Add NL→DRL Generator LLM node — Opus-class, generates DRL from NL + corpus examples
- [x] Task 5: Add Validate DRL node — HTTP POST to shadow-rules-mcp for parse/compile validation
- [x] Task 6: Add Validation Check IF node — branches on valid/invalid DRL
- [x] Task 7: Add Reflection Fix LLM node — Opus-class, corrects DRL based on validation error message
- [x] Task 8: Add Re-validate node — HTTP POST to shadow-rules-mcp for re-validation
- [x] Task 9: Add Retry Counter IF node — limits reflection loop to max 3 iterations
- [x] Task 10: Add Load Shadow Rule node — HTTP POST to shadow-rules-mcp `loadShadowRule(drl)`
- [x] Task 11: Add Replay Historical Events node — HTTP POST to shadow-rules-mcp `replayHistoricalEvents(window)`
- [x] Task 12: Add Read Shadow Results node — HTTP GET from shadow-rules-mcp `readShadowRiskResults()`
- [x] Task 13: Add Diff Against Production node — HTTP GET from shadow-rules-mcp `diffAgainstProduction()`
- [x] Task 14: Add Impact Explainer LLM node — Opus-class, explains diff in business terms
- [x] Task 15: Add HITL Gate Wait node — pauses for deploy approval, resumes on webhook
- [x] Task 16: Add Approval Check IF node — branches on APPROVED vs DENIED
- [x] Task 17: Add Hand Off to Deploy node — HTTP POST to deployment pipeline notification
- [x] Task 18: Add Archive Results node — HTTP POST to store shadow results for reference
- [x] Task 19: Add Respond to Webhook node — returns impact report
- [x] Task 20: Wire all connections including reflection loop and HITL branching
- [x] Task 21: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/specialized-shadow-rule-simulator.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] Reflection loop correctly limited to 3 iterations
- [x] HITL gate correctly blocks production deployment
- [x] Connections reference existing node names
