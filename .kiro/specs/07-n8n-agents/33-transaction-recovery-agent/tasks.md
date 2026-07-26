# Tasks — Transaction Recovery Coordinator Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/transaction-recovery.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Transaction Recovery Coordinator"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/transaction-recovery`, receives `tradeId`, `requestType`, `correlationId`
- [ ] Task 3: Add Verify No Settlement node — HTTP GET to settlement-mcp `verifyNoSettlement()`
- [ ] Task 4: Add Check Replay Key node — HTTP GET to trade-lifecycle-mcp `checkReplayKey()`
- [ ] Task 5: Add Gather State node — HTTP GET to state-reconciliation-mcp for multi-system state
- [ ] Task 6: Add Investigation LLM node — Opus-class, produces investigation report with divergences
- [ ] Task 7: Add Recovery Planner LLM node — Opus-class, produces ordered recovery plan
- [ ] Task 8: Add Plan Approval Wait node — HITL gate for plan approval, 4h timeout
- [ ] Task 9: Add Plan Approval Handler IF node — branches approved/denied
- [ ] Task 10: Add Step Loop node — SplitInBatches to iterate recovery steps
- [ ] Task 11: Add Safety Check node — HTTP GET pre-step verification
- [ ] Task 12: Add Step Approval Wait node — HITL gate for high-risk steps, 1h timeout
- [ ] Task 13: Add Execute Step node — HTTP POST to appropriate MCP tool (invalidateCache/replayEvent)
- [ ] Task 14: Add Verify Post-Condition node — HTTP GET to state-reconciliation-mcp `compareState()`
- [ ] Task 15: Add Close Recovery Case node — HTTP POST to trade-lifecycle-mcp `closeRecoveryCase()`
- [ ] Task 16: Add Respond to Webhook node — returns recovery report with audit
- [ ] Task 17: Wire all connections including investigation parallel, plan gate, step loop, and step gate
- [ ] Task 18: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/transaction-recovery.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] Plan-level HITL gate blocks all execution
- [ ] Step-level HITL gate blocks individual steps
- [ ] Loop correctly iterates recovery steps
- [ ] Connections reference existing node names
