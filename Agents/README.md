# Agents

n8n workflow JSON exports for the FX Trade Operations Intelligence platform's AI agent layer.

## Boundary

This directory contains **n8n workflow JSON exports only**. No application code, no runtime logic, and no credential values are stored here.

- Agent orchestration logic lives in the n8n workflow definitions
- Tool implementations live in the `Middleware/` Java services
- Detection/analysis models live in `Sidecars/` Python packages
- Credential values are managed in the n8n runtime, never committed to source

## Directory Structure

```
Agents/
├── README.md
├── workflows/
│   ├── README.md
│   ├── supervisor/     — Orchestration-level workflows
│   ├── specialized/    — Domain-specific agent workflows
│   └── utilities/      — Helper/utility workflows
└── credentials/
    └── README.md       — Credential type references (no values)
```

## Workflow Import

Workflows are imported into n8n via the platform's REST API or CLI at deployment time. See `DevOps/Local/` for the local n8n compose configuration.

## Notes

- All identifiers use the synthetic `FX-` prefix (e.g., `FX-000001`).
- No real financial data or credentials are committed.
