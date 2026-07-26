# Tasks — Schema & Contract Drift Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/schema-contract-drift.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Schema Contract Drift Agent"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/schema-contract-drift`, receives `subject`, `version`, `type`, `correlationId`
- [ ] Task 3: Add Get Schema Compatibility node — HTTP GET to schema-registry-mcp `getSchemaCompatibility(subject, ver)`
- [ ] Task 4: Add Find Consumers node — HTTP GET to schema-registry-mcp `findConsumersOf(topic)`
- [ ] Task 5: Add Simulate Payload node — HTTP POST to schema-registry-mcp `simulatePayloadAgainstConsumers()`
- [ ] Task 6: Add Impact Analyzer LLM node — Opus-class, analyzes consumer impact and business flow degradation
- [ ] Task 7: Add Breaking Change Check IF node — branches if breaking change detected
- [ ] Task 8: Add Flag Approval Wait node — HITL gate for breaking-change flag, 4h timeout
- [ ] Task 9: Add Approval Handler IF node — branches approved/denied
- [ ] Task 10: Add Flag Breaking Change node — HTTP POST to schema-registry-mcp `flagBreakingChange()`
- [ ] Task 11: Add Respond to Webhook node — returns drift report
- [ ] Task 12: Wire all connections including HITL branch paths
- [ ] Task 13: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/schema-contract-drift.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] HITL gate blocks flag execution
- [ ] All node types use valid n8n type identifiers
- [ ] Connections reference existing node names
