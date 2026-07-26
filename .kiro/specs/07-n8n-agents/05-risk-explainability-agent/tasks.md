# Tasks — Trade Risk Explainability Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/risk-explainability.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Risk Explainability Agent"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/risk-explainability`, receives `tradeId`, `question`, `sessionContext`, `correlationId`
- [x] Task 3: Add Fetch Risk Result node — HTTP GET to risk-calculation-mcp `getRiskResult(tradeId)`, returns current/previous scores and contributing factors
- [x] Task 4: Add Fetch Rule Trace node — HTTP GET to risk-calculation-mcp `getRuleTrace(tradeId)`, returns rule execution details
- [x] Task 5: Add Fetch Market Snapshot node — HTTP GET to market-data-mcp `getMarketSnapshot(pair, timestamp)`, runs in parallel with nodes 3-4
- [x] Task 6: Add Fetch Trade Characteristics node — HTTP GET to trade-lifecycle-mcp `getTradeCharacteristics(tradeId)`
- [x] Task 7: Add Rule Trace Translator LLM node — Haiku-class, translates raw rule trace to business-readable JSON array
- [x] Task 8: Add Similar Case Retrieval node — HTTP POST to vector-search endpoint with risk factors as query
- [x] Task 9: Add Explanation Synthesizer LLM node — Opus-class, produces ExplanationEnvelope with ranked factors, summary, similar cases, provenance
- [x] Task 10: Add Respond to Webhook node — returns final ExplanationEnvelope to caller
- [x] Task 11: Wire all connections: Trigger → parallel (Risk Result, Rule Trace, Market Snapshot, Trade Chars) → Translator → Similar Cases → Synthesizer → Response
- [x] Task 12: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/risk-explainability.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] All node types use valid n8n type identifiers
- [x] Connections reference existing node names
- [x] Importable into n8n (structure validated)
