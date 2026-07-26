# ADR-0023: Neo4j on EKS over Amazon Neptune for Graph Workloads

**Status:** Accepted

**Date:** 2024-02-19

## Context

The platform uses a graph database for trade relationship mapping: counterparty networks, trade chains (e.g., FX-000101 → FX-000102 novation), and dependency visualization. Agents query the graph to understand trade context and identify cascading failures.

Two options were evaluated:

1. **Amazon Neptune** — fully managed graph database (Gremlin/SPARQL/openCypher).
2. **Neo4j on EKS** — self-managed Neo4j Community/Enterprise on Kubernetes.

## Decision

We adopt **Neo4j Community Edition on EKS** for graph workloads, deployed via the Neo4j Helm chart.

### Implementation

| Configuration | Value |
|--------------|-------|
| Version | Neo4j 5.x Community |
| Deployment | Helm chart on EKS (dedicated NodePool) |
| Storage | EBS gp3, 200GB |
| Query language | Cypher |
| Access | Bolt protocol from MCP server only |
| Backup | Kubernetes CronJob → S3 (daily dump) |

### Graph Model (Subset)

```
(:Trade {id: "FX-005192"}) -[:COUNTERPARTY]-> (:Entity {id: "FX-CP-EMEA-01"})
(:Trade {id: "FX-005192"}) -[:NOVATED_TO]-> (:Trade {id: "FX-005200"})
(:Trade {id: "FX-005192"}) -[:BOOKED_IN]-> (:Book {id: "FX-BOOK-EU-01"})
(:Entity {id: "FX-CP-EMEA-01"}) -[:PARENT_OF]-> (:Entity {id: "FX-CP-EMEA-01-SUB"})
```

### Query Pattern

Agent asks: "Show all trades affected if FX-CP-EMEA-01 defaults"
→ Cypher: `MATCH (e:Entity {id: "FX-CP-EMEA-01"})<-[:COUNTERPARTY]-(t:Trade) RETURN t`

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Amazon Neptune | No native Cypher support until recently (openCypher subset only); vendor lock-in to Gremlin/SPARQL; higher cost for small graph workloads; development team experienced in Cypher |
| Neptune with openCypher | Subset of Cypher — missing APOC procedures, full-text indexing patterns team relies on |
| PostgreSQL with recursive CTEs | Adequate for simple traversals but poor for multi-hop relationship queries at scale |

## Consequences

### Positive
- Full Cypher language support including APOC library for complex graph algorithms
- Team's existing Neo4j/Cypher expertise reduces onboarding time
- Lower cost than Neptune for our graph size (< 10M nodes) — single EKS pod vs managed service minimum
- Same Helm-based deployment model as other platform components
- Local development uses same Neo4j Docker image (parity with production)

### Negative
- Self-managed: team responsible for upgrades, monitoring, and backup
- No managed HA — Community Edition is single-instance (no clustering)
- EBS storage requires capacity planning and IOPS configuration
- No managed cross-AZ replication

### Mitigations
- Graph is rebuild-able from Kafka event stream — not the system of record (PostgreSQL is)
- Daily S3 backup + Kafka replay provides recovery path (RPO < 24h)
- Prometheus metrics via Neo4j metrics endpoint for proactive monitoring
- Future: upgrade to Neo4j Enterprise for clustering if graph workload grows significantly
