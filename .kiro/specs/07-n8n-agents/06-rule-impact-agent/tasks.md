# Tasks — Runtime Business Rule Impact Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/rule-impact.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Rule Impact Agent"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/rule-impact`, receives anomaly envelope from sidecar with `ruleId`, `ruleVersion`, `deviationPct`, `affectedPairs`
- [ ] Task 3: Add Fetch Firing Stats node — HTTP GET to rules-engine-mcp `getRuleFiringStats()` for pre/post deployment windows
- [ ] Task 4: Add Compare Behavior node — HTTP GET to rules-engine-mcp `compareRuleBehavior(preVer, postVer)` for delta metrics
- [ ] Task 5: Add Find Conflicts node — HTTP GET to rules-engine-mcp `findConflictingRules()` for overlapping conditions
- [ ] Task 6: Add Impact Analyzer LLM node — Opus-class, produces structured causal analysis JSON (root cause, scope, impact, classification)
- [ ] Task 7: Add Simulate Rollback node — HTTP GET to rules-engine-mcp `simulateRule()` with previous version
- [ ] Task 8: Add Similar Defect Retrieval node — HTTP POST to vector-search endpoint for prior rule anomalies
- [ ] Task 9: Add Rollback Proposal LLM node — Opus-class, produces RollbackProposal envelope
- [ ] Task 10: Add HITL Gate IF node — checks if rollback is recommended and anomaly is material
- [ ] Task 11: Add Wait for Approval node — pauses execution, resumes on approval webhook
- [ ] Task 12: Add Approval Handler IF node — branches on APPROVED vs DENIED
- [ ] Task 13: Add Execute Rollback node — HTTP POST to rules-engine-mcp `requestRuleRollback()` with approval reference
- [ ] Task 14: Add Log Denial node — HTTP POST to audit store logging denial decision
- [ ] Task 15: Add Respond to Webhook node — returns impact report to caller
- [ ] Task 16: Wire all connections according to execution flow diagram
- [ ] Task 17: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/rule-impact.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] All node types use valid n8n type identifiers
- [ ] Connections reference existing node names
- [ ] HITL gate correctly blocks rollback execution
