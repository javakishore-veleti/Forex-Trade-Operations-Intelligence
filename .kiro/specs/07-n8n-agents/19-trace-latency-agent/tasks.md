# Tasks — Distributed-Trace Latency Explanation Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/trace-latency.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Trace Latency Agent"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/trace-latency`, receives `tradeId`, `question`, `slaBreach`
- [x] Task 3: Add Get Trade Trace node — HTTP GET to trace-mcp `getTradeTrace(tradeId)`
- [x] Task 4: Add Get Span Breakdown node — HTTP GET to trace-mcp `getSpanBreakdown()`
- [x] Task 5: Add Get Service Baselines node — HTTP GET to trace-mcp `getServiceBaseline(span)` for each slow span
- [x] Task 6: Add Correlate to Deploy node — HTTP GET to change-mcp `correlateToDeploy()` for slow service
- [x] Task 7: Add Trace Perception LLM node — Haiku-class, parses span tree into structured SpanFact objects
- [x] Task 8: Add Root Cause Synthesizer LLM node — Opus-class, produces LatencyReport with root cause chain
- [x] Task 9: Add Respond to Webhook node — returns LatencyReport
- [x] Task 10: Wire all connections: Trigger → Trace → Breakdown → parallel (Baselines, Deploy) → Perception → Synthesizer → Response
- [x] Task 11: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/trace-latency.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] Two-LLM chain: Haiku (perception) → Opus (reasoning)
- [x] No HITL gate present (Risk L agent)
- [x] Connections reference existing node names
