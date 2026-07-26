# Tasks — Consumer-Lag SLA Predictor Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/consumer-lag-predictor.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Consumer Lag SLA Predictor"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/consumer-lag-predictor`, receives `region`, `consumerGroup`, `correlationId`
- [x] Task 3: Add Get Lag By Partition node — HTTP GET to streaming-mcp `getLagByPartition()`
- [x] Task 4: Add Get Completion Forecast node — HTTP GET to streaming-mcp `getCompletionForecast()` (Python sidecar)
- [x] Task 5: Add Get Hot Partition Keys node — HTTP GET to streaming-mcp `getHotPartitionKeys()`
- [x] Task 6: Add Scaling Planner LLM node — Opus-class, evaluates SLA risk and proposes scaling
- [x] Task 7: Add SLA At Risk IF node — branches if forecast exceeds cutoff
- [x] Task 8: Add Scale Approval Wait node — HITL gate for scaling approval, 30min timeout
- [x] Task 9: Add Approval Handler IF node — branches approved/denied
- [x] Task 10: Add Request Scale node — HTTP POST to streaming-mcp `requestReplicaScale(from, to)`
- [x] Task 11: Add Respond to Webhook node — returns forecast report + decision
- [x] Task 12: Wire all connections including HITL branch paths
- [x] Task 13: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/consumer-lag-predictor.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] HITL gate blocks scaling execution
- [x] All node types use valid n8n type identifiers
- [x] Connections reference existing node names
