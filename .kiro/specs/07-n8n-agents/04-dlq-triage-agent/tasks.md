# Tasks — DLQ Triage & Remediation Agent Workflow Implementation

## Workflow File
`Agents/workflows/specialized/specialized-dlq-triage.workflow.json`

---

## Tasks

- [x] Task 1: Create workflow skeleton with metadata (`name`, `settings`, `active`, `id`)
- [x] Task 2: Add Webhook Trigger node — POST `/webhook/dlq-cluster`, receives clustered FailureSignature payload from dlq-cluster-analyzer sidecar
- [x] Task 3: Add Validate Payload IF node — checks required fields (`clusters[]` with `cluster_id`, `count`, `representative_trace`)
- [x] Task 4: Add Log Error & Stop node — respondToWebhook with 400 for malformed payloads
- [x] Task 5: Add Split Clusters node — splitInBatches to iterate over each failure cluster
- [x] Task 6: Add Classify Cluster LLM node — deep reasoning model for transient vs poison classification with confidence scoring
- [x] Task 7: Add Classification Router Switch node — routes to replay path (TRANSIENT) or quarantine path (POISON)
- [x] Task 8: Add Check Divergence HTTP Request node — POST to `http://state-reconciliation-service:8082/mcp/evaluateCanonicalState`
- [x] Task 9: Add Generate Replay Proposal LLM node — drafts replay proposal with impact summary for human review
- [x] Task 10: Add Generate Quarantine Proposal LLM node — drafts quarantine explanation with investigation steps
- [x] Task 11: Add HITL Gate (Replay) Wait node — pauses for human approval before replay execution
- [x] Task 12: Add HITL Gate (Quarantine) Wait node — pauses for human approval before quarantine execution
- [x] Task 13: Add Replay Messages HTTP Request node — POST to `http://state-reconciliation-service:8082/mcp/replayDlqMessage` (Risk M, with approvalReference)
- [x] Task 14: Add Quarantine Messages HTTP Request node — POST to `http://state-reconciliation-service:8082/mcp/quarantineMessage` (Risk M, with approvalReference)
- [x] Task 15: Add Log Denial Set nodes — records denial for reviewed-not-replayed state
- [x] Task 16: Add Respond to Webhook node — returns triage summary
- [x] Task 17: Wire all connections between nodes (batch loop + classification branches + approval branches)
- [x] Task 18: Set realistic node positions (x, y coordinates) for visual layout

## Verification

- [x] JSON is valid: `python3 -c "import json; json.load(open('Agents/workflows/specialized/specialized-dlq-triage.workflow.json'))"`
- [x] Contains required top-level fields: `name`, `nodes`, `connections`, `settings`
- [x] All node types use valid n8n type identifiers
- [x] Webhook path matches sidecar-webhooks.md: `/webhook/dlq-cluster`
- [x] HITL Wait nodes present for both replay and quarantine paths
- [x] Connections reference existing node names
