# Tasks — Settlement-Fail Prediction Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/specialized-settlement-fail-predictor.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Settlement Fail Predictor"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/settlement-fail-predictor`, receives `window`, `region`
- [x] Task 3: Add Get Approaching Settlements node — HTTP GET to settlement-mcp for trades in window
- [x] Task 4: Add Loop node — SplitInBatches to iterate per trade
- [x] Task 5: Add Check Missing SSI node — HTTP GET to settlement-mcp `getMissingSSI(tradeId)`
- [x] Task 6: Add Check Nostro Shortfall node — HTTP GET to settlement-mcp `getNostroShortfall(ccy)`
- [x] Task 7: Add Predict Fail Probability node — HTTP GET to settlement-mcp `predictFailProbability()` (calls Python ML)
- [x] Task 8: Add Risk Ranker & Explainer LLM node — Opus-class, produces ranked EscalationPackage
- [x] Task 9: Add High-Risk Check IF node — branches if any probability > 0.7
- [x] Task 10: Add Escalation Gate Wait node — HITL for escalation approval
- [x] Task 11: Add Escalation Handler IF node — branches approved/denied
- [x] Task 12: Add Escalate Risk node — HTTP POST to settlement-mcp `escalateSettlementRisk()`
- [x] Task 13: Add Respond to Webhook node — returns risk report
- [x] Task 14: Wire all connections including loop and HITL branches
- [x] Task 15: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/specialized-settlement-fail-predictor.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] Loop correctly iterates per trade
- [x] HITL gate blocks escalation execution
- [x] Connections reference existing node names
