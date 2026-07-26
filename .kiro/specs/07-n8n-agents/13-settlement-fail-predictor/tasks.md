# Tasks — Settlement-Fail Prediction Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/settlement-fail-predictor.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Settlement Fail Predictor"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/settlement-fail-predictor`, receives `window`, `region`
- [ ] Task 3: Add Get Approaching Settlements node — HTTP GET to settlement-mcp for trades in window
- [ ] Task 4: Add Loop node — SplitInBatches to iterate per trade
- [ ] Task 5: Add Check Missing SSI node — HTTP GET to settlement-mcp `getMissingSSI(tradeId)`
- [ ] Task 6: Add Check Nostro Shortfall node — HTTP GET to settlement-mcp `getNostroShortfall(ccy)`
- [ ] Task 7: Add Predict Fail Probability node — HTTP GET to settlement-mcp `predictFailProbability()` (calls Python ML)
- [ ] Task 8: Add Risk Ranker & Explainer LLM node — Opus-class, produces ranked EscalationPackage
- [ ] Task 9: Add High-Risk Check IF node — branches if any probability > 0.7
- [ ] Task 10: Add Escalation Gate Wait node — HITL for escalation approval
- [ ] Task 11: Add Escalation Handler IF node — branches approved/denied
- [ ] Task 12: Add Escalate Risk node — HTTP POST to settlement-mcp `escalateSettlementRisk()`
- [ ] Task 13: Add Respond to Webhook node — returns risk report
- [ ] Task 14: Wire all connections including loop and HITL branches
- [ ] Task 15: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/settlement-fail-predictor.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] Loop correctly iterates per trade
- [ ] HITL gate blocks escalation execution
- [ ] Connections reference existing node names
