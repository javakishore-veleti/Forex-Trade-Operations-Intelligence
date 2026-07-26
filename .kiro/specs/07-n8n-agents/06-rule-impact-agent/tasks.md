# Tasks — Runtime Business Rule Impact Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/rule-impact.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Rule Impact Agent"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/rule-impact`, receives anomaly envelope from sidecar with `ruleId`, `ruleVersion`, `deviationPct`, `affectedPairs`
- [x] Task 3: Add Fetch Firing Stats node — HTTP GET to rules-engine-mcp `getRuleFiringStats()` for pre/post deployment windows
- [x] Task 4: Add Compare Behavior node — HTTP GET to rules-engine-mcp `compareRuleBehavior(preVer, postVer)` for delta metrics
- [x] Task 5: Add Find Conflicts node — HTTP GET to rules-engine-mcp `findConflictingRules()` for overlapping conditions
- [x] Task 6: Add Impact Analyzer LLM node — Opus-class, produces structured causal analysis JSON (root cause, scope, impact, classification)
- [x] Task 7: Add Simulate Rollback node — HTTP GET to rules-engine-mcp `simulateRule()` with previous version
- [x] Task 8: Add Similar Defect Retrieval node — HTTP POST to vector-search endpoint for prior rule anomalies
- [x] Task 9: Add Rollback Proposal LLM node — Opus-class, produces RollbackProposal envelope
- [x] Task 10: Add HITL Gate IF node — checks if rollback is recommended and anomaly is material
- [x] Task 11: Add Wait for Approval node — pauses execution, resumes on approval webhook
- [x] Task 12: Add Approval Handler IF node — branches on APPROVED vs DENIED
- [x] Task 13: Add Execute Rollback node — HTTP POST to rules-engine-mcp `requestRuleRollback()` with approval reference
- [x] Task 14: Add Log Denial node — HTTP POST to audit store logging denial decision
- [x] Task 15: Add Respond to Webhook node — returns impact report to caller
- [x] Task 16: Wire all connections according to execution flow diagram
- [x] Task 17: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/rule-impact.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] All node types use valid n8n type identifiers
- [x] Connections reference existing node names
- [x] HITL gate correctly blocks rollback execution
