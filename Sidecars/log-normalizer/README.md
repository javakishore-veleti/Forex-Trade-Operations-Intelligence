# Log Normalizer

Sidecar service that normalizes and structures raw FX trade operation logs into a consistent format suitable for downstream analysis, search, and alerting.

## Responsibilities

- Parse heterogeneous log formats from trade services
- Normalize timestamps, identifiers, and severity levels
- Produce structured JSON envelopes compatible with MCP_Tool_Contract

## Technology

- **Language:** Python 3.11+
- **Build:** hatchling
- **Container:** `python:3.11-slim`

## Project Structure

```
log-normalizer/
├── pyproject.toml
├── Dockerfile
├── README.md
├── src/
│   └── log_normalizer/
│       └── __init__.py
└── tests/
    └── test_smoke.py
```

## Development

```bash
pip install -e ".[dev]"
pytest
```

## Docker

```bash
docker build -t log-normalizer .
```

## Notes

- This sidecar performs detection/normalization only — no business logic.
- All example identifiers use the synthetic `FX-` prefix (e.g., `FX-000001`).
