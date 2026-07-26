# Tasks — Databricks Lineage & Freshness Impact Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/databricks-lineage.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Databricks Lineage Agent"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/databricks-lineage`, receives `table`, `jobId`, `eventType`, `region`
- [ ] Task 3: Add Get Job Status node — HTTP GET to lineage-mcp `getJobStatus()`
- [ ] Task 4: Add Get Lineage Downstream node — HTTP GET to lineage-mcp `getLineageDownstream(table)`
- [ ] Task 5: Add Get Aggregation Readiness node — HTTP GET to lineage-mcp `getAggregationReadiness(region)`
- [ ] Task 6: Add Impact Explainer LLM node — Opus-class, produces LineageImpactReport
- [ ] Task 7: Add Block Check IF node — branches if block required
- [ ] Task 8: Add Block Gate Wait node — HITL for aggregation block approval
- [ ] Task 9: Add Block Handler IF node — branches approved/denied
- [ ] Task 10: Add Block Aggregation node — HTTP POST to lineage-mcp `blockAggregation()`
- [ ] Task 11: Add Respond to Webhook node — returns impact report
- [ ] Task 12: Wire all connections including HITL branches
- [ ] Task 13: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/databricks-lineage.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] HITL gate correctly blocks aggregation
- [ ] Connections reference existing node names
