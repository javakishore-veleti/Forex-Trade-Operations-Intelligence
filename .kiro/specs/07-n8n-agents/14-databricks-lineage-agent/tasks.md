# Tasks — Databricks Lineage & Freshness Impact Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/specialized-databricks-lineage.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Databricks Lineage Agent"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/databricks-lineage`, receives `table`, `jobId`, `eventType`, `region`
- [x] Task 3: Add Get Job Status node — HTTP GET to lineage-mcp `getJobStatus()`
- [x] Task 4: Add Get Lineage Downstream node — HTTP GET to lineage-mcp `getLineageDownstream(table)`
- [x] Task 5: Add Get Aggregation Readiness node — HTTP GET to lineage-mcp `getAggregationReadiness(region)`
- [x] Task 6: Add Impact Explainer LLM node — Opus-class, produces LineageImpactReport
- [x] Task 7: Add Block Check IF node — branches if block required
- [x] Task 8: Add Block Gate Wait node — HITL for aggregation block approval
- [x] Task 9: Add Block Handler IF node — branches approved/denied
- [x] Task 10: Add Block Aggregation node — HTTP POST to lineage-mcp `blockAggregation()`
- [x] Task 11: Add Respond to Webhook node — returns impact report
- [x] Task 12: Wire all connections including HITL branches
- [x] Task 13: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/specialized-databricks-lineage.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] HITL gate correctly blocks aggregation
- [x] Connections reference existing node names
