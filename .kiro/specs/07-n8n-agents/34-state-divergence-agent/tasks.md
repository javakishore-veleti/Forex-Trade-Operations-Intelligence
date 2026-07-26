# Tasks — State Divergence Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/state-divergence.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "State Divergence Agent"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/state-divergence`, receives `tradeId`, `mode`, `correlationId`
- [x] Task 3: Add Query Postgres node — HTTP GET to trade-lifecycle-mcp `queryTradeState()`
- [x] Task 4: Add Get Mongo Doc node — HTTP GET to trade-lifecycle-mcp `getTradeDocument()`
- [x] Task 5: Add Get Redis Cache node — HTTP GET to trade-lifecycle-mcp `getCachedTradeState()`
- [x] Task 6: Add Get Kafka Event node — HTTP GET to streaming-mcp `getLatestDomainEvent()`
- [x] Task 7: Add Get Analytics node — HTTP GET to analytics-mcp `getAnalyticsTradeState()`
- [x] Task 8: Add Evaluate Canonical State node — HTTP POST to state-reconciliation-mcp `evaluateCanonicalState()`
- [x] Task 9: Add Divergence Analyzer LLM node — Opus-class, classifies and explains divergences
- [x] Task 10: Add Divergence Check IF node — branches if divergence found
- [x] Task 11: Add Reconciliation Approval Wait node — HITL gate, 4h timeout
- [x] Task 12: Add Approval Handler IF node — branches approved/denied
- [x] Task 13: Add Start Reconciliation node — HTTP POST to state-reconciliation-mcp `startReconciliation()`
- [x] Task 14: Add Respond to Webhook node — returns divergence report
- [x] Task 15: Wire connections: Trigger → parallel (5 state fetches) → Evaluate → LLM → Gate → Response
- [x] Task 16: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/state-divergence.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] Parallel state fetches execute correctly
- [x] HITL gate blocks reconciliation execution
- [x] Connections reference existing node names
