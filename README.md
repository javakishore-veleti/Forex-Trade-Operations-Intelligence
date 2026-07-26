# Forex-Trade-Operations-Intelligence

[![Java 21](https://img.shields.io/badge/Java-21_LTS-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.4](https://img.shields.io/badge/Spring_Boot-3.4-6DB33F?logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Angular 19](https://img.shields.io/badge/Angular-19-DD0031?logo=angular&logoColor=white)](https://angular.dev/)
[![n8n](https://img.shields.io/badge/n8n-Agents-FF6D5A?logo=n8n&logoColor=white)](https://n8n.io/)
[![Python 3.11+](https://img.shields.io/badge/Python-3.11+-3776AB?logo=python&logoColor=white)](https://www.python.org/)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Apache Kafka](https://img.shields.io/badge/Kafka-3.x-231F20?logo=apache-kafka&logoColor=white)](https://kafka.apache.org/)
[![Redis 7](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![MongoDB 7](https://img.shields.io/badge/MongoDB-7-47A248?logo=mongodb&logoColor=white)](https://www.mongodb.com/)
[![Drools 9](https://img.shields.io/badge/Drools-9-1E8CBE?logo=drools&logoColor=white)](https://www.drools.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Spec-Driven](https://img.shields.io/badge/Methodology-Spec_Driven_Development-blueviolet)](docs/adr/)
[![Synthetic Data Only](https://img.shields.io/badge/Data-Synthetic_Only_(FX--prefix)-green)](README.md#synthetic-data-policy)

---

Forex-Trade-Operations-Intelligence is a spec-driven, publicly available reference implementation of a runtime-intelligence platform for foreign-exchange trade operations. Built using **Kiro Spec-Driven Development** — every feature progresses through `requirements.md → design.md → tasks.md → code`.

## What's Implemented

| Layer | Components | Tests |
|-------|-----------|-------|
| **Middleware** (7 services) | shared-domain-contracts, trade-ingest, trade-lifecycle, risk-calculation, business-calendar, eod-processing, state-reconciliation | 346 Java tests |
| **Events** | Kafka topic registry, domain event model (25+ event types), event-sequence-processor (Kafka Streams), DLQ management | 95 tests |
| **Portals** (3 apps) | Admin (trade investigation, EOD dashboard, risk aggregation, exceptions, HITL approvals), TraderDesk (status, risk explanation, position, book view), FXTradeBlotter (live position, exposure, settlement, counterparty) | Angular 19 |
| **Observability** | OTel tracing (Jaeger), Prometheus + Grafana (8 dashboards, 6 alert rules), ELK log correlation (4 saved queries) | Config-as-code |
| **Agents** (4 MVP + 30 specced) | Supervisor, Trade Lifecycle Reconstruction, Canary Probe, DLQ Triage | n8n workflow JSON |
| **Sidecars** (4 detectors) | KPI Anomaly, DLQ Cluster Analyzer, Capacity Forecast, Log Normalizer | 24 Python tests |
| **Local Deploy** | MCP server layer (Spring AI), sidecar→agent webhook wiring, n8n import/credential scripts, smoke test | End-to-end |

## Top-Level Directory Structure

| Directory | Role |
|-----------|------|
| `Middleware/` | Java 21 / Spring Boot microservices — 7 services + parent Maven POM |
| `Portals/` | Three Angular 19 standalone portal applications (Admin, TraderDesk, FXTradeBlotter) |
| `Agents/` | n8n workflow JSON exports — supervisor, specialized, and utility agent workflows |
| `Sidecars/` | Python 3.11+ detection and embedding sidecar packages |
| `DevOps/Local/` | Docker Compose per infrastructure role (9 services) + orchestration scripts |
| `docs/` | ADRs, observability docs, event schema catalogue, diagrams |
| `.kiro/specs/` | **Spec-driven development** — 71 specs with requirements, design, and tasks |
| `.github/` | CODEOWNERS + CI workflow placeholders |

## Spec-Driven Development (Kiro Methodology)

This repo demonstrates **full-lifecycle spec-driven development**:

```
.kiro/specs/
├── MASTER-PLAN.md                    ← project-wide progress tracking
├── 01-initial-setup/                 ← technology stack + repo skeleton
├── architecture-golden-path/         ← cross-cutting NFRs inherited by all services
├── 02-microservices/                 ← 7 bounded contexts (DDD)
├── 03-events/                        ← Kafka topics, event schemas, sequence processor, DLQ
├── 04-portals/                       ← 3 Angular portal feature specs
├── 05-observability/                 ← OTel tracing, metrics, logging
├── 06-local-deploy/                  ← MCP server, sidecars, n8n wiring
└── 07-n8n-agents/                    ← 34 agent specs (requirements + design + tasks)
```

See [Kiro-Understanding.md](Kiro-Understanding.md) for the full methodology documentation.

## Architecture Overview

```mermaid
flowchart TB
    subgraph Portals["Portals (Angular 19)"]
        Admin[Admin Portal]
        Desk[TraderDesk]
        Blotter[FX Blotter]
    end

    subgraph Agents["Agent Layer (n8n)"]
        Sup[Supervisor Agent]
        Spec[34 Specialized Agents]
        HITL[HITL Approval Gate]
    end

    subgraph Middleware["Middleware (Spring Boot / Java 21)"]
        Ingest[Trade Ingest]
        Life[Trade Lifecycle]
        Risk[Risk Calculation]
        EOD[EOD Processing]
        Cal[Business Calendar]
        Recon[State Reconciliation]
        Seq[Event Sequence Processor]
    end

    subgraph Sidecars["Detection Sidecars (Python)"]
        KPI[KPI Anomaly]
        DLQ[DLQ Cluster]
        Cap[Capacity Forecast]
        Log[Log Normalizer]
    end

    subgraph Infra["Infrastructure"]
        PG[(PostgreSQL)]
        Mongo[(MongoDB)]
        Redis[(Redis)]
        Neo4j[(Neo4j)]
        Vec[(pgvector)]
        Kafka[Kafka]
    end

    Portals -->|REST API| Middleware
    Agents -->|MCP Tools| Middleware
    Sidecars -->|Webhook| Agents
    Middleware --> Kafka
    Middleware --> PG & Mongo & Redis
    Seq --> Kafka
    Agents -.->|Risk M/H| HITL
```

> See [docs/diagrams/](docs/diagrams/) for detailed C4, data-flow, and infrastructure diagrams.

## Quick Start

```bash
# Start all local infrastructure (Postgres, Kafka, Redis, MongoDB, Neo4j, ELK, Prometheus, Grafana, n8n)
npm run start

# Build all microservices
mvn -f Middleware/pom.xml verify

# Check infrastructure status
npm run status

# Stop everything
npm run stop
```

## Architectural Constraints

- **Spring Boot only** for microservices — all business/transactional logic
- **n8n only** for AI agents — workflow JSON exports
- **Python only** for sidecars — detection/embedding, never business logic
- **LLMs never compute official numbers** — risk, exposure, state from deterministic services
- **Every M/H-risk agent action** → propose → simulate → impact report → human approval → execute

See [docs/adr/0001-monorepo-language-boundaries.md](docs/adr/0001-monorepo-language-boundaries.md) for the full ADR.

## Synthetic Data Policy

All examples, identifiers, and test data use synthetic `FX-` prefixed identifiers (e.g., FX-000001). No real financial institution, person, or confidential data is committed.
