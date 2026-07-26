# Tasks — Consumer-Lag SLA Predictor Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/consumer-lag-predictor.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Consumer Lag SLA Predictor"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/consumer-lag-predictor`, receives `region`, `consumerGroup`, `correlationId`
- [ ] Task 3: Add Get Lag By Partition node — HTTP GET to streaming-mcp `getLagByPartition()`
- [ ] Task 4: Add Get Completion Forecast node — HTTP GET to streaming-mcp `getCompletionForecast()` (Python sidecar)
- [ ] Task 5: Add Get Hot Partition Keys node — HTTP GET to streaming-mcp `getHotPartitionKeys()`
- [ ] Task 6: Add Scaling Planner LLM node — Opus-class, evaluates SLA risk and proposes scaling
- [ ] Task 7: Add SLA At Risk IF node — branches if forecast exceeds cutoff
- [ ] Task 8: Add Scale Approval Wait node — HITL gate for scaling approval, 30min timeout
- [ ] Task 9: Add Approval Handler IF node — branches approved/denied
- [ ] Task 10: Add Request Scale node — HTTP POST to streaming-mcp `requestReplicaScale(from, to)`
- [ ] Task 11: Add Respond to Webhook node — returns forecast report + decision
- [ ] Task 12: Wire all connections including HITL branch paths
- [ ] Task 13: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/consumer-lag-predictor.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] HITL gate blocks scaling execution
- [ ] All node types use valid n8n type identifiers
- [ ] Connections reference existing node names
