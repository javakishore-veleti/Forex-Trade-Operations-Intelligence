# Tasks — Supervisor Agent Workflow Implementation

## Workflow File
`Agents/workflows/supervisor/supervisor-trade-operations.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/supervisor-chat`, receives `sessionId`, `message`, `userId`, `correlationId`
- [x] Task 3: Add Load Session Memory node — HTTP GET to Redis cache API to load session context by `sessionId`
- [x] Task 4: Add Intent Classifier LLM node — mid-tier model with system prompt for intent classification, outputs structured JSON with `intent`, `confidence`, `extractedEntities`
- [x] Task 5: Add Intent Router Switch node — routes based on `intent` field to: TRADE_LIFECYCLE, DLQ_TRIAGE, CANARY_PROBE, UNKNOWN
- [x] Task 6: Add Execute Sub-Agent HTTP nodes — one per sub-agent (Trade Lifecycle, DLQ Triage, Canary Probe) calling respective webhook endpoints
- [x] Task 7: Add Clarification LLM node — generates clarifying question when intent is UNKNOWN
- [x] Task 8: Add HITL Risk Check IF node — checks `riskLevel` in sub-agent response for M/H
- [x] Task 9: Add Wait for Approval node — pauses execution, resumes on approval webhook
- [x] Task 10: Add Approval Handler IF node — branches on APPROVED vs DENIED
- [x] Task 11: Add Response Synthesizer LLM node — deep reasoning model synthesizes final coherent answer from AgentEnvelope(s)
- [x] Task 12: Add Save Session Memory node — HTTP PUT to Redis cache API to persist updated session state
- [x] Task 13: Add Respond to Webhook node — returns final response to caller
- [x] Task 14: Wire all connections between nodes according to the execution flow
- [x] Task 15: Set realistic node positions (x, y coordinates) for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/supervisor/supervisor-trade-operations.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] All node types use valid n8n type identifiers
- [x] Connections reference existing node names
- [x] Importable into n8n (structure validated)
