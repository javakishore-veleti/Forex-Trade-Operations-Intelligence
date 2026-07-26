# Tasks — Exception Materiality Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/exception-materiality.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Exception Materiality Agent"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/exception-materiality`, receives `region`, `cutoffTime`
- [ ] Task 3: Add Get Unresolved Exceptions node — HTTP GET to exception-mcp `getUnresolvedExceptions()`
- [ ] Task 4: Add Loop node — SplitInBatches to iterate per exception
- [ ] Task 5: Add Get Exposure node — HTTP GET to risk-calculation-mcp `getExposure(tradeId)` per exception
- [ ] Task 6: Add Classify Materiality node — HTTP GET to rules-engine-mcp `classifyMateriality()` per exception
- [ ] Task 7: Add Classification Explainer LLM node — Opus-class, produces MaterialityReport with blockers, non-material, reviewRequired
- [ ] Task 8: Add Non-Material Check IF node — branches if non-material exceptions exist
- [ ] Task 9: Add Bulk Approval Wait node — HITL gate for non-material exception approval
- [ ] Task 10: Add Approval Handler IF node — branches approved/denied
- [ ] Task 11: Add Approve Batch node — HTTP POST to exception-mcp `approveExceptionBatch(ids)`
- [ ] Task 12: Add Respond to Webhook node — returns MaterialityReport
- [ ] Task 13: Wire all connections including loop and HITL branches
- [ ] Task 14: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/exception-materiality.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] Loop correctly iterates per exception
- [ ] HITL gate blocks until approval for non-material batch
- [ ] Connections reference existing node names
