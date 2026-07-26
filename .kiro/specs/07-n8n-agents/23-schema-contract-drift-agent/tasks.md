# Tasks — Schema & Contract Drift Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/schema-contract-drift.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Schema Contract Drift Agent"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/schema-contract-drift`, receives `subject`, `version`, `type`, `correlationId`
- [x] Task 3: Add Get Schema Compatibility node — HTTP GET to schema-registry-mcp `getSchemaCompatibility(subject, ver)`
- [x] Task 4: Add Find Consumers node — HTTP GET to schema-registry-mcp `findConsumersOf(topic)`
- [x] Task 5: Add Simulate Payload node — HTTP POST to schema-registry-mcp `simulatePayloadAgainstConsumers()`
- [x] Task 6: Add Impact Analyzer LLM node — Opus-class, analyzes consumer impact and business flow degradation
- [x] Task 7: Add Breaking Change Check IF node — branches if breaking change detected
- [x] Task 8: Add Flag Approval Wait node — HITL gate for breaking-change flag, 4h timeout
- [x] Task 9: Add Approval Handler IF node — branches approved/denied
- [x] Task 10: Add Flag Breaking Change node — HTTP POST to schema-registry-mcp `flagBreakingChange()`
- [x] Task 11: Add Respond to Webhook node — returns drift report
- [x] Task 12: Wire all connections including HITL branch paths
- [x] Task 13: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/schema-contract-drift.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] HITL gate blocks flag execution
- [x] All node types use valid n8n type identifiers
- [x] Connections reference existing node names
