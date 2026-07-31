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

> **Agents propose. Services execute. Humans approve. Portals visualize.**

A runtime-intelligence platform where AI agents observe live FX trade operations, reconstruct business state across distributed services, reason about anomalies, and coordinate safe recovery — all under human control.

---

## Purpose of this Repo

This repository has two complementary purposes:

1. **Runtime FX intelligence** — a **public, synthetic reference implementation** of a platform where AI agents add operational intelligence around foreign-exchange trade systems **without replacing them**.
2. **Specification-Driven Development (Kiro)** — a working example of building that platform with Kiro SDD: technology-agnostic requirements → design → atomic tasks → code, backed by ADRs and hooks in `.kiro/`.

Concretely, the repo demonstrates:

- **Deterministic microservices first** — Spring Boot services own trade ingest, lifecycle, risk calculation, EOD processing, calendars, reconciliation, and event sequencing
- **Agents that observe and propose** — n8n hosts a supervisor plus specialized agents that investigate anomalies and recommend actions from a fixed catalogue
- **Human-in-the-loop control** — medium/high-risk actions require approval in the Admin Portal before any service executes them
- **Detection beside, not inside, the trade path** — Python sidecars flag statistical anomalies and trigger agent workflows; they never process trades
- **Operator-facing portals** — Angular apps for trading desk, blotter, and ops/risk administration
- **Spec-Driven Development with Kiro** — every feature is specified before implementation (`.kiro/specs/`), so the same artifacts guide humans and AI coding tools; see [Spec-Driven Development](#spec-driven-development-kiro-methodology) and [Kiro-Understanding.md](Kiro-Understanding.md)

It is intentionally **not** a chatbot demo, a generic “ask your database” sample, or a workplace export. All identifiers and scenarios are fictional (`FX-` prefix). Use it to study agent-assisted FX operations, practice Kiro-style SDD, or adapt either pattern for your own systems.

---

## Table of Contents

- [Purpose of this Repo](#purpose-of-this-repo)
- [The Core Idea](#the-core-idea)
- [Architecture](#architecture)
- [What Makes This Different](#what-makes-this-different)
- [The Agent Fleet](#the-agent-fleet)
- [Spec-Driven Development](#spec-driven-development-kiro-methodology)
- [Quick Start](#quick-start)
- [What's Implemented](#whats-implemented)
- [Architectural Constraints](#architectural-constraints)
- [Synthetic Data Policy](#synthetic-data-policy)

---

## The Core Idea

A foreign-exchange trade passes through 9 stages across 7 microservices. When something goes wrong — a trade stalls, risk spikes, an event goes missing, a region can't close end-of-day — **34 AI agents** investigate, explain, and propose corrective action. But they never act alone:

1. **Sidecar detects** — Python statistical detectors flag anomalies
2. **Agent investigates** — n8n workflow calls MCP tools on Spring Boot services
3. **Agent proposes** — LLM reasons about root cause, suggests action from a fixed catalogue
4. **Human approves** — Ops/Risk Manager reviews in the Admin Portal, clicks Approve
5. **Service executes** — deterministic Spring Boot service performs the action with the approval token

No LLM ever computes a risk figure, moves money, or advances trade state. Those are deterministic. Agents add intelligence without compromising correctness.

---

## Architecture

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
    HITL -.->|approval request| Admin
    Admin -.->|approve/reject| HITL
```

> See [docs/diagrams/](docs/diagrams/) for C4 system context, container view, trade lifecycle flow, agent architecture, and local infrastructure diagrams.

---

## What Makes This Different

| Aspect | This Platform | Typical AI Agent Demos |
|--------|--------------|----------------------|
| **Risk boundary** | LLMs never compute numbers — deterministic services do | LLM does everything |
| **Action safety** | Every M/H action gated by human approval | Autonomous execution |
| **Permitted actions** | Fixed enum catalogue — agents select, never invent | LLM generates arbitrary actions |
| **Observability** | Full OTel tracing + 8 Grafana dashboards + ELK | Console.log |
| **Spec-driven** | 71 specs with requirements → design → tasks → code | Code-first, docs maybe later |
| **Multi-store reconciliation** | Canonical state from event history, not majority vote | Single DB assumed |
| **Production-grade** | Kafka transactional producers, idempotency, DLQ, circuit breakers | Happy-path only |

---

## The Agent Fleet

34 agents organized by operational concern:

| Theme | Agents | Risk |
|-------|--------|------|
| **Trade Lifecycle & State** | Lifecycle Reconstruction, State Divergence, Event Integrity, Duplicate Effect Guard, Amendment Ripple | L–H |
| **Risk & Rules** | Risk Explainability, Rule Impact, Rule Coverage, Shadow Rule Simulator, Counterparty Exposure | L–H |
| **EOD & Readiness** | EOD Readiness, Data Freshness, Exception Materiality, Settlement Fail Predictor, Databricks Lineage, Regulatory Reporting | M–H |
| **Event & Data Integrity** | DLQ Triage, Consumer Lag Predictor, Market Data Staleness, Schema Drift, Cutoff Calendar, Retry Storm | M–H |
| **Observability → Business** | Change Correlation, Business KPI Guard, Trace Latency, Canary Probe, Runtime Intent | L–M |
| **Dependency & Blast Radius** | Contagion Analysis, Service Genome | L |
| **Capacity & Economics** | Capacity Backlog, Adaptive Routing, FinOps Cost | H |
| **Recovery** | Transaction Recovery Coordinator | H |
| **Interface** | Supervisor (routes to all others) | L (inherits) |

---

## Spec-Driven Development (Kiro Methodology)

Every feature in this repo progressed through:

```
requirements.md  →  design.md  →  tasks.md  →  code
```

- **Requirements** are technology-agnostic (reference Technology Roles, not product names)
- **Design** resolves roles to concrete products (the agnostic → concrete boundary)
- **Tasks** are atomic, ordered, independently verifiable (a living checklist)
- **Code** is generated from tasks, verified per section, committed incrementally

71 specs. 46 ADRs. 5 Kiro hooks. Full methodology documented in [Kiro-Understanding.md](Kiro-Understanding.md).

---

## Use Cases & Personas

This platform serves **8 personas** across **50 documented use cases** — see [docs/use-cases.md](docs/use-cases.md) for full details.

### Personas

| Persona | Portal | Role |
|---|---|---|
| FX Trader | TraderDesk | Views trade status, risk explanations, positions |
| Broker / Sales Desk | FX Blotter | Monitors live positions, settlement, counterparty exposure |
| Operations Staff | Admin | Investigates trades, manages EOD, triages exceptions |
| Risk Manager | Admin | Monitors risk aggregations, approves limit exceptions |
| Platform Administrator | Admin + Grafana | Manages infrastructure, scaling, cost |
| Rules Owner / Quant | Admin | Deploys rules, reviews impact, simulates changes |
| Compliance Officer | Admin | Verifies reporting completeness, audit trail |
| SRE / Dev Team | Grafana + Kibana + Jaeger | Traces, metrics, logs, deployment correlation |

### Use Cases by Category

| Category | Count | Key Agents Involved |
|---|---|---|
| **Trade Lifecycle & Investigation** | 10 | Supervisor, Trade Lifecycle Reconstruction, State Divergence, Event Integrity, Duplicate Effect Guard, Amendment Ripple |
| **Risk & Rules** | 8 | Risk Explainability, Rule Impact, Rule Coverage, Shadow Rule Simulator, Counterparty Exposure |
| **End-of-Day Operations** | 7 | EOD Readiness, Exception Materiality, Data Freshness, Business Calendar |
| **Event & Data Integrity** | 8 | DLQ Triage, Consumer Lag Predictor, Market Data Staleness, Schema Drift, Cutoff Calendar, Event Integrity |
| **Observability & Correlation** | 7 | Business KPI Guard, Change Correlation, Trace Latency, Canary Probe, Runtime Intent, Service Genome, Contagion Analysis |
| **Capacity, Cost & Recovery** | 7 | Capacity Backlog, Retry Storm, FinOps Cost, Adaptive Routing, Transaction Recovery, Duplicate Effect Guard, Settlement Fail Predictor |
| **Multi-Agent & Supervisor** | 3 | Supervisor (routes to all others based on intent) |
| **Total** | **50** | **34 agents** |

> Full use case details with step-by-step portal interactions, agent invocation order, HITL gates, and outcomes: **[docs/use-cases.md](docs/use-cases.md)**

---

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

---

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

---

## Architectural Constraints

- **Spring Boot only** for microservices — all business/transactional logic
- **n8n only** for AI agents — workflow JSON exports
- **Python only** for sidecars — detection/embedding, never business logic
- **LLMs never compute official numbers** — risk, exposure, state from deterministic services
- **Every M/H-risk agent action** → propose → simulate → impact report → human approval → execute

See [docs/adr/0001-monorepo-language-boundaries.md](docs/adr/0001-monorepo-language-boundaries.md) for the full ADR.

---

## Synthetic Data Policy

All examples, identifiers, and test data use synthetic `FX-` prefixed identifiers (e.g., FX-000001). No real financial institution, person, or confidential data is committed.
