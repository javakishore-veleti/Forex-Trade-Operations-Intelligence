# Tasks — Duplicate Business-Effect Guard Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/duplicate-effect-guard.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Duplicate Effect Guard Agent"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/duplicate-effect-guard`, receives `tradeId`, `key`, `triggerType`, `correlationId`
- [ ] Task 3: Add Check Idempotency node — HTTP GET to trade-lifecycle-mcp `checkIdempotencyConsumed(key)`
- [ ] Task 4: Add Find Double Booking node — HTTP GET to trade-lifecycle-mcp `findDoubleBooking(tradeId)`
- [ ] Task 5: Add Find Duplicate Settlement node — HTTP GET to settlement-mcp `findDuplicateSettlementInstruction()`
- [ ] Task 6: Add Effect Classifier LLM node — Opus-class, classifies REAL vs BENIGN
- [ ] Task 7: Add Real Double Check IF node — branches if REAL_DOUBLE_EFFECT
- [ ] Task 8: Add Dry-Run Reversal node — HTTP POST to trade-lifecycle-mcp `reverseDuplicateEffect(dryRun=true)`
- [ ] Task 9: Add Reversal Approval Wait node — HITL gate, 2h timeout
- [ ] Task 10: Add Approval Handler IF node — branches approved/denied
- [ ] Task 11: Add Execute Reversal node — HTTP POST to trade-lifecycle-mcp `reverseDuplicateEffect(dryRun=false)`
- [ ] Task 12: Add Respond to Webhook node — returns duplicate report
- [ ] Task 13: Wire all connections including parallel checks and HITL branch
- [ ] Task 14: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/duplicate-effect-guard.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] HITL gate blocks reversal execution
- [ ] Dry-run executes before HITL gate
- [ ] Connections reference existing node names
