# Tasks — Adaptive Transaction Routing Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/adaptive-routing.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Adaptive Transaction Routing Agent"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/adaptive-routing`, receives `region`, `trigger`, `correlationId`
- [x] Task 3: Add Get Runtime Conditions node — HTTP GET to observability-mcp `getRuntimeConditions()`
- [x] Task 4: Add Condition Analyzer LLM node — Opus-class, assesses if routing change warranted
- [x] Task 5: Add Propose Routing Policy node — HTTP POST to routing-mcp `proposeRoutingPolicy()`
- [x] Task 6: Add Validate Routing Policy node — HTTP POST to routing-mcp `validateRoutingPolicy()`
- [x] Task 7: Add Action Check IF node — branches if valid and needed
- [x] Task 8: Add Policy Approval Wait node — HITL gate for routing approval, 15min timeout
- [x] Task 9: Add Approval Handler IF node — branches approved/denied
- [x] Task 10: Add Apply Routing Config node — HTTP POST to routing-mcp `applyRoutingConfig()`
- [x] Task 11: Add Respond to Webhook node — returns routing decision
- [x] Task 12: Wire all connections including HITL branch paths
- [x] Task 13: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/adaptive-routing.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] HITL gate blocks policy application
- [x] All node types use valid n8n type identifiers
- [x] Connections reference existing node names
