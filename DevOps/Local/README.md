# DevOps/Local

Docker Compose configurations for the FX Trade Operations Intelligence local development stack.

## Roles & Default Ports

| Role | Directory | Image | Port(s) | Network |
|------|-----------|-------|---------|---------|
| Relational Store | `relational-store/` | `postgres:16.4` | 5432 | `fxops-relational-net` |
| Cache | `cache/` | `redis:7.4` | 6379 | `fxops-cache-net` |
| Document Store | `document-store/` | `mongo:7.0` | 27017 | `fxops-document-net` |
| Graph Store | `graph-store/` | `neo4j:5.23` | 7474, 7687 | `fxops-graph-net` |
| Event Stream | `event-stream/` | `apache/kafka:3.8.0` | 9092 | `fxops-event-net` |
| Agent Platform | `agent-platform/` | `n8nio/n8n:1.55.0` | 5678 | `fxops-agent-net` |
| Observability Logging | `observability-logging/` | `elasticsearch/logstash/kibana:8.15.0` | 9200, 5044, 5601 | `fxops-logging-net` |
| Observability Metrics | `observability-metrics/` | `prom/prometheus:v2.54.0`, `grafana/grafana:11.1.0` | 9090, 3000 | `fxops-metrics-net` |

## Network Naming Convention

All compose networks follow the pattern `fxops-{role}-net` where `{role}` identifies the infrastructure concern.

## Usage

Start an individual role:

```bash
cd DevOps/Local/<role-directory>
docker compose up -d
```

Start all roles (dependency order):

```bash
./docker-all-up.sh
```

Stop all roles (reverse order):

```bash
./docker-all-down.sh
```

Check status:

```bash
./all-status.sh
```

## Image Version Policy

All images use **pinned version tags** — the `:latest` tag is never used. Version bumps require explicit PR review.

## Notes

- All credentials in these files are for local development only.
- No real financial data or secrets are committed.
- All identifiers use the synthetic `FX-` prefix.
