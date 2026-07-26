# Workflows

n8n workflow JSON exports organized by agent role.

## Directory Structure

```
workflows/
├── supervisor/    — Orchestration/supervisor agent workflows
├── specialized/   — Domain-specific agent workflows (risk, settlement, etc.)
└── utilities/     — Utility/helper agent workflows (notifications, sync, etc.)
```

## Naming Convention

Workflow files follow this pattern:

```
{role}-{domain-action}.workflow.json
```

Examples:
- `supervisor-trade-operations.workflow.json`
- `specialized-risk-assessment.workflow.json`
- `utilities-notification-dispatch.workflow.json`

## Workflow JSON Structure

Each workflow export must be a valid n8n workflow JSON with at minimum:

```json
{
  "name": "Human-readable workflow name",
  "nodes": [],
  "connections": {},
  "settings": {}
}
```

## Notes

- These are **export-only** files — they are imported into n8n at deployment time.
- No credentials or secrets are embedded in workflow JSON.
- All identifiers use the synthetic `FX-` prefix.
