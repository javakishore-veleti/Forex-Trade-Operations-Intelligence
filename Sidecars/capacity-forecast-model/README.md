# capacity-forecast-model

Statistical capacity forecasting sidecar for FX trade infrastructure planning.

## Purpose

Forecasts future resource capacity needs (queue depth, connection pool usage,
throughput saturation) based on historical time-series data. Emits a compact
anomaly envelope compatible with `MCP_Tool_Contract` when projected capacity
exceeds a configured threshold. This sidecar performs detection only — it
contains no business logic, no trade processing, and no agent orchestration.

## Inputs

- **Capacity metrics** — time-series observations of resource utilization, tagged
  with synthetic `FX-` identifiers where applicable.
- **Configuration** — forecast horizon, confidence interval, saturation thresholds.

## Outputs

When a capacity breach is forecasted, the sidecar emits a JSON envelope
conforming to the `MCP_Tool_Contract` schema with forecast details.

## Build and Run

```bash
# Install in development mode
pip install -e ".[dev]"

# Run tests
pytest

# Build Docker image
docker build -t capacity-forecast-model:latest .

# Run container
docker run --rm capacity-forecast-model:latest
```

## Project Structure

```
capacity-forecast-model/
├── pyproject.toml
├── Dockerfile
├── src/
│   └── capacity_forecast_model/
│       └── __init__.py
├── tests/
│   └── test_smoke.py
└── README.md
```
