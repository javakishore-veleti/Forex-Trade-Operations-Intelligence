# Tasks — Consumer-Lag SLA Predictor Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/consumer-lag-predictor.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Consumer Lag Predictor"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/consumer-lag-predictor`, receives `consumerGroup`, `currentLag`, `partitions`, `cutoffDeadline`
- [ ] Task 3: Add Get Lag by Partition node — HTTP GET to kafka-mcp `getLagByPartition()`
- [ ] Task 4: Add Get Completion Forecast node — HTTP GET to kafka-mcp `getCompletionForecast()` (calls Python ML)
- [ ] Task 5: Add Get Hot Partition Keys node — HTTP GET to kafka-mcp `getHotPartitionKeys()` (parallel with nodes 3-4)
- [ ] Task 6: Add Scaling Advisor LLM node — Opus-class, produces ScalingProposal with willMiss, cause, proposal, cost
- [ ] Task 7: Add Cutoff Check IF node — branches if cutoff miss predicted
- [ ] Task 8: Add Scaling Gate Wait node — HITL for scaling approval (30 min timeout)
- [ ] Task 9: Add Scale Handler IF node — branches approved/denied
- [ ] Task 10: Add Scale Replicas node — HTTP POST to infra-mcp `requestReplicaScale(from, to)`
- [ ] Task 11: Add Defer Processing node — HTTP POST deferral notification
- [ ] Task 12: Add Respond to Webhook node — returns forecast report
- [ ] Task 13: Wire all connections including parallel data fetch and HITL branches
- [ ] Task 14: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/consumer-lag-predictor.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] HITL gate correctly blocks scaling execution
- [ ] Parallel execution for lag/forecast/keys nodes
- [ ] Connections reference existing node names
