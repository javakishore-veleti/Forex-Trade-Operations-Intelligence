# Tasks — Service Genome Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/service-genome.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Service Genome Agent"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/service-genome`, receives `service`, `queryType`, `correlationId`
- [ ] Task 3: Add Get Service Profile node — HTTP GET to platform-mcp `getServiceProfile(svc)`
- [ ] Task 4: Add Get Dependencies node — HTTP GET to graph-mcp `getDependencies(svc)`
- [ ] Task 5: Add Predict Fragility node — HTTP GET to platform-mcp `predictFragility(svc)` (Python sidecar)
- [ ] Task 6: Add Find Change Consumers node — HTTP GET to graph-mcp `findConsumersOfChange()`
- [ ] Task 7: Add Genome Analyzer LLM node — Opus-class, produces genome report with fragility explanation
- [ ] Task 8: Add Respond to Webhook node — returns ServiceGenomeEnvelope
- [ ] Task 9: Wire connections: Trigger → Profile → parallel (Deps, Fragility, Consumers) → LLM → Response
- [ ] Task 10: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/service-genome.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] All node types use valid n8n type identifiers
- [ ] Connections reference existing node names
- [ ] Importable into n8n (structure validated)
