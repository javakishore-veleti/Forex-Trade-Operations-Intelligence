# Sidecars

Python detection and embedding sidecar packages for the FX Trade Operations Intelligence platform.

## Boundary Rules

Sidecars are **detection and embedding only**. They perform statistical analysis, anomaly detection, log normalization, and capacity forecasting. They do **not** contain business logic, trade execution, or state management — those responsibilities belong to the Java Middleware tier.

## MCP_Tool_Contract Envelope

All sidecar outputs conform to the MCP_Tool_Contract-compatible envelope format. Each sidecar produces structured JSON responses that can be consumed by n8n agent workflows or middleware services via the standard tool-contract interface.

## Packages

| Package | Description |
|---------|-------------|
| `kpi-anomaly-detector` | Detects anomalies in FX trade KPI time-series data |
| `dlq-cluster-analyzer` | Clusters and categorizes dead-letter-queue entries |
| `capacity-forecast-model` | Forecasts system capacity based on historical load |
| `log-normalizer` | Normalizes heterogeneous trade operation logs |

## Technology

- **Language:** Python 3.11+
- **Build System:** hatchling (`pyproject.toml`)
- **Container Base:** `python:3.11-slim` (pinned)
- **Test Runner:** pytest

## Development

Each sidecar is an independent package. To develop locally:

```bash
cd Sidecars/<package-name>
pip install -e ".[dev]"
pytest
```

## Notes

- All example identifiers use the synthetic `FX-` prefix (e.g., `FX-000001`).
- No real financial data is used in any sidecar.
