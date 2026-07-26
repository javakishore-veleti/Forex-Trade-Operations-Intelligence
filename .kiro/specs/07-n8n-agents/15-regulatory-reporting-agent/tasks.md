# Tasks — Regulatory Reporting Completeness Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/specialized-regulatory-reporting.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name: "Regulatory Reporting Agent"`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/regulatory-reporting`, receives `regime`, `deadline`, `region`
- [x] Task 3: Add Get Reportable Universe node — HTTP GET to reporting-mcp `getReportableUniverse()`
- [x] Task 4: Add Get Submitted Reports node — HTTP GET to reporting-mcp `getSubmittedReports()` (parallel with node 3)
- [x] Task 5: Add Find Gaps node — HTTP GET to reporting-mcp `findReportingGaps()`
- [x] Task 6: Add Get Validation Failures node — HTTP GET to reporting-mcp `getFieldValidationFailures()`
- [x] Task 7: Add Gap Explainer LLM node — Opus-class, produces CompletenessReport with gaps, patterns, validation issues
- [x] Task 8: Add Gaps Check IF node — branches if correctable gaps exist
- [x] Task 9: Add Resubmit Gate Wait node — HITL for resubmission approval
- [x] Task 10: Add Approval Handler IF node — branches approved/denied
- [x] Task 11: Add Resubmit Report node — HTTP POST to reporting-mcp `resubmitReport()`
- [x] Task 12: Add Respond to Webhook node — returns CompletenessReport
- [x] Task 13: Wire all connections including parallel data fetch and HITL branches
- [x] Task 14: Set node positions for visual layout in n8n editor

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/specialized-regulatory-reporting.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] HITL gate correctly blocks resubmission
- [x] Connections reference existing node names
