# ADR-0020: AWS Aurora PostgreSQL over Standard RDS

**Status:** Accepted

**Date:** 2024-02-18

## Context

The platform requires PostgreSQL for multiple services: trade-lifecycle, risk-calculation, state-reconciliation, eod-processing, and the agent episodic memory store. Requirements include high availability, read scaling (portals query heavily), point-in-time recovery, and pgvector support for agent semantic memory.

Two options were evaluated:

1. **Amazon RDS for PostgreSQL** — single-instance or Multi-AZ with synchronous standby.
2. **Amazon Aurora PostgreSQL** — distributed storage with up to 15 read replicas and fast failover.

## Decision

We adopt **Aurora PostgreSQL (version 16-compatible)** as the managed database for all platform PostgreSQL workloads.

### Implementation

| Cluster | Purpose | Instance | Replicas |
|---------|---------|----------|----------|
| `fx-platform-primary` | Trade lifecycle, risk, state-reconciliation, EOD | db.r6g.xlarge writer + 2× reader | 2 read replicas |
| `fx-agents-episodic` | Agent episodic memory, pgvector | db.r6g.large writer + 1× reader | 1 read replica |

### Configuration

- Storage: Aurora I/O Optimized (predictable costs for high-throughput trade processing)
- Failover: Multi-AZ with < 30s failover time
- Backup: Continuous backup with 35-day PITR window
- pgvector: Extension enabled on `fx-agents-episodic` cluster for semantic search
- Connection pooling: RDS Proxy for microservice connection management

### Schema Separation

Services use separate schemas within `fx-platform-primary`:
- `trade_lifecycle` — trade states, events, audit
- `risk_calc` — position snapshots, exposure records
- `state_recon` — reconciliation results, discrepancy records
- `eod_process` — batch run metadata, settlement instructions

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| RDS PostgreSQL Multi-AZ | Slower failover (60-120s vs < 30s), no storage auto-scaling, max 5 read replicas |
| RDS PostgreSQL single-AZ | No HA; unacceptable for trade processing platform |
| Self-managed PostgreSQL on EKS | Operational burden of backups, patching, HA; team lacks DBA capacity |

## Consequences

### Positive
- Sub-30s failover minimizes trade processing disruption
- Up to 15 read replicas scale portal read traffic independently
- Storage auto-scales (no capacity planning for disk)
- Aurora I/O Optimized eliminates per-I/O billing unpredictability
- Built-in PITR enables investigation of historical states

### Negative
- Higher cost than RDS for small workloads (~20-30% premium)
- Aurora-specific behaviors (e.g., storage model) differ slightly from vanilla PostgreSQL
- Vendor lock-in to Aurora-specific features (Global Database, Serverless v2)

### Mitigations
- Application code uses standard PostgreSQL SQL — no Aurora-specific features in queries
- Cost premium justified by HA requirements and operational savings
- Local development uses standard PostgreSQL (Docker Compose) — Aurora differences are infrastructure-only
