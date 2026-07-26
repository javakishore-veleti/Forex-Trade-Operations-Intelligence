# dlq-cluster-analyzer

Statistical clustering sidecar for dead-letter-queue (DLQ) error categorization.

## Purpose

Analyzes DLQ messages to identify recurring error patterns and cluster them by
similarity. Emits a compact anomaly envelope compatible with `MCP_Tool_Contract`
when a new error cluster is detected or an existing cluster experiences a spike.
This sidecar performs detection only — it contains no business logic, no trade
processing, and no agent orchestration.

## Inputs

- **DLQ messages** — failed message payloads with error metadata, tagged with
  synthetic `FX-` trade identifiers.
- **Configuration** — clustering parameters (similarity threshold, minimum cluster
  size, time window).

## Outputs

When a significant cluster is identified, the sidecar emits a JSON envelope
conforming to the `MCP_Tool_Contract` schema with cluster details.

## Build and Run

```bash
# Install in development mode
pip install -e ".[dev]"

# Run tests
pytest

# Build Docker image
docker build -t dlq-cluster-analyzer:latest .

# Run container
docker run --rm dlq-cluster-analyzer:latest
```

## Project Structure

```
dlq-cluster-analyzer/
├── pyproject.toml
├── Dockerfile
├── src/
│   └── dlq_cluster_analyzer/
│       └── __init__.py
├── tests/
│   └── test_smoke.py
└── README.md
```
