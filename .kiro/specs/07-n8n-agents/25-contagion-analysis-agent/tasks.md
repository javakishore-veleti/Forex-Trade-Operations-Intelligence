# Tasks — Contagion Analysis Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/contagion-analysis.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Contagion Analysis Agent"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/contagion-analysis`, receives `entity`, `entityType`, `correlationId`
- [x] Task 3: Add Find Trade Dependencies node — HTTP GET to graph-mcp `findTradeDependencies()`
- [x] Task 4: Add Find Affected Books node — HTTP GET to graph-mcp `findAffectedBooks()`
- [x] Task 5: Add Find Downstream Aggregations node — HTTP GET to graph-mcp `findDownstreamAggregations()`
- [x] Task 6: Add Find Shared Dependencies node — HTTP GET to graph-mcp `findSharedMarketDataDependencies()`
- [x] Task 7: Add Calculate Blast Radius node — HTTP GET to graph-mcp `calculateBusinessBlastRadius()`
- [x] Task 8: Add Impact Narrator LLM node — Opus-class, produces contagion narrative with severity
- [x] Task 9: Add Respond to Webhook node — returns BlastRadiusEnvelope
- [x] Task 10: Wire connections: Trigger → parallel (Trade Deps, Books, Aggregations, Shared Deps) → Calculate → LLM → Response
- [x] Task 11: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/contagion-analysis.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] All node types use valid n8n type identifiers
- [x] Connections reference existing node names
- [x] Importable into n8n (structure validated)
