# ADR-0026: Azure Cosmos DB MongoDB API over Self-Managed MongoDB on AKS

**Status:** Accepted

**Date:** 2024-02-20

## Context

MongoDB stores agent investigation documents, n8n execution metadata, and observability correlation data. For Azure deployment, we need a MongoDB-compatible store. The platform uses MongoDB 7 features locally (document validation, change streams, aggregation pipelines).

Two options were evaluated:

1. **Cosmos DB with MongoDB API (vCore)** — Azure-managed MongoDB-compatible service.
2. **Self-managed MongoDB on AKS** — MongoDB Community/Enterprise deployed via Helm/operator on Kubernetes.

## Decision

We adopt **Cosmos DB for MongoDB vCore** for Azure deployment.

### Implementation

| Configuration | Value |
|--------------|-------|
| API | MongoDB vCore (not RU-based) |
| Tier | M40 (4 vCores, 16GB RAM) |
| Storage | 128GB SSD |
| HA | 2-node replica set (primary + secondary) |
| MongoDB compatibility | 7.0 |
| Network | Private endpoint in AKS VNet |

### Why vCore (not RU-based Cosmos DB)

- vCore model provides predictable pricing (no surprise RU throttling)
- Full MongoDB wire protocol compatibility (aggregation pipeline, change streams)
- Native MongoDB drivers work without modification
- Supports vector search (for agent semantic memory, replacing pgvector on Azure)

### Collections

| Collection | Database | Purpose |
|-----------|----------|---------|
| `agent_investigations` | `fx_agents` | Full investigation documents with embedded evidence |
| `n8n_executions` | `fx_agents` | n8n execution metadata for audit |
| `log_correlations` | `fx_observability` | Correlated log entries across services |
| `sidecar_detections` | `fx_observability` | Raw detection outputs from Python sidecars |

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Self-managed MongoDB on AKS | Operational burden: backup, HA, patching, storage management; no team DBA expertise for MongoDB |
| Cosmos DB RU-based | Unpredictable cost for variable write patterns; throttling during EOD bursts; limited aggregation support |
| Replace MongoDB with PostgreSQL JSONB | Would require rewriting document queries; loses change streams used for real-time updates to portals |

## Consequences

### Positive
- Zero operational overhead: patching, backup, HA handled by Azure
- Full MongoDB 7.0 compatibility — same queries work locally and in Azure
- Built-in vector search eliminates need for separate pgvector in Azure
- Predictable monthly cost (vCore pricing vs per-operation RU billing)
- Private endpoint keeps traffic within VNet

### Negative
- Higher cost than self-managed for equivalent compute (~40% premium)
- vCore availability limited to certain Azure regions
- Some advanced MongoDB features (e.g., sharding) work differently than native MongoDB
- Vendor lock-in to Cosmos DB vCore API specifics

### Mitigations
- Application uses standard MongoDB driver with no Cosmos-specific extensions
- Region constraints mapped during deployment planning (vCore available in all target regions)
- Local development uses standard MongoDB 7 Docker image — compatibility tested in CI
