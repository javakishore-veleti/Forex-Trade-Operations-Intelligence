# Tasks — Transaction Recovery Coordinator Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/transaction-recovery.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Transaction Recovery Coordinator"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/transaction-recovery`, receives `tradeId`, `requestType`, `correlationId`
- [x] Task 3: Add Verify No Settlement node — HTTP GET to settlement-mcp `verifyNoSettlement()`
- [x] Task 4: Add Check Replay Key node — HTTP GET to trade-lifecycle-mcp `checkReplayKey()`
- [x] Task 5: Add Gather State node — HTTP GET to state-reconciliation-mcp for multi-system state
- [x] Task 6: Add Investigation LLM node — Opus-class, produces investigation report with divergences
- [x] Task 7: Add Recovery Planner LLM node — Opus-class, produces ordered recovery plan
- [x] Task 8: Add Plan Approval Wait node — HITL gate for plan approval, 4h timeout
- [x] Task 9: Add Plan Approval Handler IF node — branches approved/denied
- [x] Task 10: Add Step Loop node — SplitInBatches to iterate recovery steps
- [x] Task 11: Add Safety Check node — HTTP GET pre-step verification
- [x] Task 12: Add Step Approval Wait node — HITL gate for high-risk steps, 1h timeout
- [x] Task 13: Add Execute Step node — HTTP POST to appropriate MCP tool (invalidateCache/replayEvent)
- [x] Task 14: Add Verify Post-Condition node — HTTP GET to state-reconciliation-mcp `compareState()`
- [x] Task 15: Add Close Recovery Case node — HTTP POST to trade-lifecycle-mcp `closeRecoveryCase()`
- [x] Task 16: Add Respond to Webhook node — returns recovery report with audit
- [x] Task 17: Wire all connections including investigation parallel, plan gate, step loop, and step gate
- [x] Task 18: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/transaction-recovery.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] Plan-level HITL gate blocks all execution
- [x] Step-level HITL gate blocks individual steps
- [x] Loop correctly iterates recovery steps
- [x] Connections reference existing node names
