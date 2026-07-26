# Tasks — Data Freshness & Decision-Suitability Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/data-freshness.workflow.json`

---

## Tasks

- [ ] Task 1: Create workflow skeleton with metadata (`name: "Data Freshness Agent"`, `settings`, `active`, `id`)
- [ ] Task 2: Add Webhook Trigger node — POST `/webhook/data-freshness`, receives `datasets[]`, `process`, `region`
- [ ] Task 3: Add Fetch Freshness node — HTTP GET to data-catalog-mcp `getDatasetFreshness(ds)` per dataset
- [ ] Task 4: Add Fetch Completeness node — HTTP GET to data-catalog-mcp `getCompleteness(ds)` per dataset
- [ ] Task 5: Add Fetch Authoritativeness node — HTTP GET to data-catalog-mcp `getAuthoritativeness(ds)` per dataset
- [ ] Task 6: Add Get Suitability Verdict node — HTTP GET to data-policy-mcp `getSuitabilityVerdict(ds, process)`
- [ ] Task 7: Add Verdict Check IF node — branches on BLOCK vs ACCEPT
- [ ] Task 8: Add Impact Explainer LLM node — Opus-class, explains block reason and impact for approver
- [ ] Task 9: Add Override Gate Wait node — pauses for human override decision
- [ ] Task 10: Add Override Handler IF node — branches on APPROVED vs DENIED
- [ ] Task 11: Add Record Exception node — HTTP POST to audit store for approved overrides
- [ ] Task 12: Add Respond to Webhook node — returns suitability report (ACCEPT/BLOCK + details)
- [ ] Task 13: Wire all connections including BLOCK/ACCEPT branching and override loop
- [ ] Task 14: Set node positions for visual layout in n8n editor

## Verification

- [ ] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/data-freshness.workflow.json'))"`
- [ ] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [ ] HITL gate correctly blocks process on BLOCK verdict
- [ ] Fail-safe: service unavailability defaults to BLOCK
- [ ] Connections reference existing node names
