# Tasks — Service Genome Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/service-genome.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Service Genome Agent"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/service-genome`, receives `service`, `queryType`, `correlationId`
- [x] Task 3: Add Get Service Profile node — HTTP GET to platform-mcp `getServiceProfile(svc)`
- [x] Task 4: Add Get Dependencies node — HTTP GET to graph-mcp `getDependencies(svc)`
- [x] Task 5: Add Predict Fragility node — HTTP GET to platform-mcp `predictFragility(svc)` (Python sidecar)
- [x] Task 6: Add Find Change Consumers node — HTTP GET to graph-mcp `findConsumersOfChange()`
- [x] Task 7: Add Genome Analyzer LLM node — Opus-class, produces genome report with fragility explanation
- [x] Task 8: Add Respond to Webhook node — returns ServiceGenomeEnvelope
- [x] Task 9: Wire connections: Trigger → Profile → parallel (Deps, Fragility, Consumers) → LLM → Response
- [x] Task 10: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/service-genome.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] All node types use valid n8n type identifiers
- [x] Connections reference existing node names
- [x] Importable into n8n (structure validated)
