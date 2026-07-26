# Tasks — Distributed-Trace Latency Explanation Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/trace-latency.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Trace Latency Agent"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/trace-latency`, receives `tradeId`, `question`, `slaBreach`
- [ ] Task 3: Add Get Trade Trace node — HTTP GET to trace-mcp `getTradeTrace(tradeId)`
- [ ] Task 4: Add Get Span Breakdown node — HTTP GET to trace-mcp `getSpanBreakdown()`
- [ ] Task 5: Add Get Service Baselines node — HTTP GET to trace-mcp `getServiceBaseline(span)` for each slow span
- [ ] Task 6: Add Correlate to Deploy node — HTTP GET to change-mcp `correlateToDeploy()` for slow service
- [ ] Task 7: Add Trace Perception LLM node — Haiku-class, parses span tree into structured SpanFact objects
- [ ] Task 8: Add Root Cause Synthesizer LLM node — Opus-class, produces LatencyReport with root cause chain
- [ ] Task 9: Add Respond to Webhook node — returns LatencyReport
- [ ] Task 10: Wire all connections: Trigger → Trace → Breakdown → parallel (Baselines, Deploy) → Perception → Synthesizer → Response
- [ ] Task 11: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/trace-latency.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] Two-LLM chain: Haiku (perception) → Opus (reasoning)
- [ ] No HITL gate present (Risk L agent)
- [ ] Connections reference existing node names
