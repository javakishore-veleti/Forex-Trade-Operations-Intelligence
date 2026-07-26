# Tasks — State Divergence Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/state-divergence.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "State Divergence Agent"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/state-divergence`, receives `tradeId`, `mode`, `correlationId`
- [ ] Task 3: Add Query Postgres node — HTTP GET to trade-lifecycle-mcp `queryTradeState()`
- [ ] Task 4: Add Get Mongo Doc node — HTTP GET to trade-lifecycle-mcp `getTradeDocument()`
- [ ] Task 5: Add Get Redis Cache node — HTTP GET to trade-lifecycle-mcp `getCachedTradeState()`
- [ ] Task 6: Add Get Kafka Event node — HTTP GET to streaming-mcp `getLatestDomainEvent()`
- [ ] Task 7: Add Get Analytics node — HTTP GET to analytics-mcp `getAnalyticsTradeState()`
- [ ] Task 8: Add Evaluate Canonical State node — HTTP POST to state-reconciliation-mcp `evaluateCanonicalState()`
- [ ] Task 9: Add Divergence Analyzer LLM node — Opus-class, classifies and explains divergences
- [ ] Task 10: Add Divergence Check IF node — branches if divergence found
- [ ] Task 11: Add Reconciliation Approval Wait node — HITL gate, 4h timeout
- [ ] Task 12: Add Approval Handler IF node — branches approved/denied
- [ ] Task 13: Add Start Reconciliation node — HTTP POST to state-reconciliation-mcp `startReconciliation()`
- [ ] Task 14: Add Respond to Webhook node — returns divergence report
- [ ] Task 15: Wire connections: Trigger → parallel (5 state fetches) → Evaluate → LLM → Gate → Response
- [ ] Task 16: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/state-divergence.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] Parallel state fetches execute correctly
- [ ] HITL gate blocks reconciliation execution
- [ ] Connections reference existing node names
