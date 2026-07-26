# Tasks — Capacity & Backlog Planning Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/capacity-backlog.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Capacity Backlog Planning Agent"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/capacity-backlog`, receives `region`, `correlationId`
- [ ] Task 3: Add Get Backlog node — HTTP GET to processing-mcp `getBacklog(region)`
- [ ] Task 4: Add Get Completion Estimate node — HTTP GET to processing-mcp `getCompletionEstimate()` (Python sidecar)
- [ ] Task 5: Add Capacity Planner LLM node — Opus-class, analyzes bottleneck and recommends action
- [ ] Task 6: Add Deadline Check IF node — branches if estimate exceeds deadline
- [ ] Task 7: Add Propose Scaling Plan node — HTTP POST to processing-mcp `proposeScalingPlan()`
- [ ] Task 8: Add Plan Approval Wait node — HITL gate for scaling approval, 20min timeout
- [ ] Task 9: Add Approval Handler IF node — branches approved/denied
- [ ] Task 10: Add Apply Scaling Plan node — HTTP POST to processing-mcp `applyScalingPlan()`
- [ ] Task 11: Add Respond to Webhook node — returns capacity report
- [ ] Task 12: Wire all connections including HITL branch paths
- [ ] Task 13: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/capacity-backlog.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] HITL gate blocks scaling execution
- [ ] All node types use valid n8n type identifiers
- [ ] Connections reference existing node names
