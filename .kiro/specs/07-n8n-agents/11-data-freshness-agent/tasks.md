# Tasks — Data Freshness & Decision-Suitability Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/specialized-data-freshness.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Data Freshness Agent"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/data-freshness`, receives `datasets[]`, `process`, `region`
- [x] Task 3: Add Fetch Freshness node — HTTP GET to data-catalog-mcp `getDatasetFreshness(ds)` per dataset
- [x] Task 4: Add Fetch Completeness node — HTTP GET to data-catalog-mcp `getCompleteness(ds)` per dataset
- [x] Task 5: Add Fetch Authoritativeness node — HTTP GET to data-catalog-mcp `getAuthoritativeness(ds)` per dataset
- [x] Task 6: Add Get Suitability Verdict node — HTTP GET to data-policy-mcp `getSuitabilityVerdict(ds, process)`
- [x] Task 7: Add Verdict Check IF node — branches on BLOCK vs ACCEPT
- [x] Task 8: Add Impact Explainer LLM node — Opus-class, explains block reason and impact for approver
- [x] Task 9: Add Override Gate Wait node — pauses for human override decision
- [x] Task 10: Add Override Handler IF node — branches on APPROVED vs DENIED
- [x] Task 11: Add Record Exception node — HTTP POST to audit store for approved overrides
- [x] Task 12: Add Respond to Webhook node — returns suitability report (ACCEPT/BLOCK + details)
- [x] Task 13: Wire all connections including BLOCK/ACCEPT branching and override loop
- [x] Task 14: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/specialized-data-freshness.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] HITL gate correctly blocks process on BLOCK verdict
- [x] Fail-safe: service unavailability defaults to BLOCK
- [x] Connections reference existing node names
