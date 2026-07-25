# kpi-anomaly-detector

Statistical anomaly detection sidecar for FX trade KPI time-series data.

## Purpose

Monitors KPI metrics (e.g., settlement latency, trade volume, rejection rate) and
emits a compact anomaly envelope compatible with `MCP_Tool_Contract` when a
statistical threshold is breached. This sidecar performs detection only — it
contains no business logic, no trade processing, and no agent orchestration.

## Inputs

- **KPI time-series data** — a stream of numeric observations tagged with a
  `businessEntity` identifier (synthetic `FX-` id) and a metric name.
- **Configuration** — detection thresholds (z-score bounds, window size, minimum
  sample count).

## Outputs

When an anomaly is detected, the sidecar emits a JSON envelope conforming to
the `MCP_Tool_Contract` schema. Example:

```json
{
  "requestId": "req-00042",
  "businessEntity": "FX-000042",
  "status": "ANOMALY_DETECTED",
  "facts": {
    "metric": "settlement_latency_ms",
    "observedValue": 847.3,
    "rollingMean": 312.6,
    "rollingStdDev": 89.1,
    "zScore": 6.0,
    "windowSize": 100,
    "sampleCount": 100
  },
  "violations": [
    {
      "rule": "Z_SCORE_UPPER_BOUND",
      "threshold": 3.0,
      "actual": 6.0,
      "message": "Settlement latency for FX-000042 exceeds 3-sigma upper bound"
    }
  ],
  "evidence": {
    "detectorVersion": "0.1.0",
    "algorithm": "rolling_z_score",
    "evaluatedAt": "2025-01-15T14:32:07.123Z"
  },
  "dataClassification": "INTERNAL",
  "expiresAt": "2025-01-15T15:32:07.123Z"
}
```

All identifiers in the example above are synthetic (`FX-000042`, `req-00042`).
No real trade data or production identifiers are used.

## Build and Run

```bash
# Install in development mode
pip install -e ".[dev]"

# Run tests
pytest

# Build Docker image
docker build -t kpi-anomaly-detector:latest .

# Run container
docker run --rm kpi-anomaly-detector:latest
```

## Project Structure

```
kpi-anomaly-detector/
├── pyproject.toml
├── Dockerfile
├── src/
│   └── kpi_anomaly_detector/
│       └── __init__.py
├── tests/
│   └── test_smoke.py
└── README.md
```
