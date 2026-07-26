# Tasks — Capacity & Backlog Planning Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/capacity-backlog.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Capacity Backlog Planning Agent"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/capacity-backlog`, receives `region`, `correlationId`
- [x] Task 3: Add Get Backlog node — HTTP GET to processing-mcp `getBacklog(region)`
- [x] Task 4: Add Get Completion Estimate node — HTTP GET to processing-mcp `getCompletionEstimate()` (Python sidecar)
- [x] Task 5: Add Capacity Planner LLM node — Opus-class, analyzes bottleneck and recommends action
- [x] Task 6: Add Deadline Check IF node — branches if estimate exceeds deadline
- [x] Task 7: Add Propose Scaling Plan node — HTTP POST to processing-mcp `proposeScalingPlan()`
- [x] Task 8: Add Plan Approval Wait node — HITL gate for scaling approval, 20min timeout
- [x] Task 9: Add Approval Handler IF node — branches approved/denied
- [x] Task 10: Add Apply Scaling Plan node — HTTP POST to processing-mcp `applyScalingPlan()`
- [x] Task 11: Add Respond to Webhook node — returns capacity report
- [x] Task 12: Wire all connections including HITL branch paths
- [x] Task 13: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/capacity-backlog.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] HITL gate blocks scaling execution
- [x] All node types use valid n8n type identifiers
- [x] Connections reference existing node names
