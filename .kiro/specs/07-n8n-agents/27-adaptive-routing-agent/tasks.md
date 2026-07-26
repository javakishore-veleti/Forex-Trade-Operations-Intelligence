# Tasks — Adaptive Transaction Routing Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/adaptive-routing.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Adaptive Transaction Routing Agent"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/adaptive-routing`, receives `region`, `trigger`, `correlationId`
- [ ] Task 3: Add Get Runtime Conditions node — HTTP GET to observability-mcp `getRuntimeConditions()`
- [ ] Task 4: Add Condition Analyzer LLM node — Opus-class, assesses if routing change warranted
- [ ] Task 5: Add Propose Routing Policy node — HTTP POST to routing-mcp `proposeRoutingPolicy()`
- [ ] Task 6: Add Validate Routing Policy node — HTTP POST to routing-mcp `validateRoutingPolicy()`
- [ ] Task 7: Add Action Check IF node — branches if valid and needed
- [ ] Task 8: Add Policy Approval Wait node — HITL gate for routing approval, 15min timeout
- [ ] Task 9: Add Approval Handler IF node — branches approved/denied
- [ ] Task 10: Add Apply Routing Config node — HTTP POST to routing-mcp `applyRoutingConfig()`
- [ ] Task 11: Add Respond to Webhook node — returns routing decision
- [ ] Task 12: Wire all connections including HITL branch paths
- [ ] Task 13: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/adaptive-routing.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] HITL gate blocks policy application
- [ ] All node types use valid n8n type identifiers
- [ ] Connections reference existing node names
