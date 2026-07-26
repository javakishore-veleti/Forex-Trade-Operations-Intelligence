# Container Architecture

```mermaid
C4Container
    title Forex Trade Operations Intelligence — Container View

    Person(user, "User", "Trader / Ops / Risk Manager")

    Container_Boundary(portals, "Portals (Angular 19)") {
        Container(admin, "Admin Portal", "Angular", "Trade investigation, EOD, exceptions, HITL approvals")
        Container(desk, "TraderDesk Portal", "Angular", "Trade status, risk explanation, positions")
        Container(blotter, "FX Blotter Portal", "Angular", "Live positions, exposure, settlement")
    }

    Container_Boundary(agents, "Agent Layer (n8n)") {
        Container(supervisor, "Supervisor Agent", "n8n workflow", "Intent routing, session memory")
        Container(specialized, "34 Specialized Agents", "n8n workflows", "Investigation, detection, coordination")
    }

    Container_Boundary(middleware, "Middleware (Spring Boot / Java 21)") {
        Container(ingest, "Trade Ingest", "Spring Boot", "Capture, validate, publish")
        Container(lifecycle, "Trade Lifecycle", "Spring Boot", "State machine, audit")
        Container(risk, "Risk Calculation", "Spring Boot", "Drools, aggregation, limits")
        Container(eod, "EOD Processing", "Spring Boot", "Close orchestration")
        Container(calendar, "Business Calendar", "Spring Boot", "DST-aware calendar authority")
        Container(recon, "State Reconciliation", "Spring Boot", "Canonical state, divergence")
        Container(seqproc, "Event Sequence Processor", "Kafka Streams", "Anomaly detection")
    }

    Container_Boundary(sidecars, "Sidecars (Python 3.11+)") {
        Container(kpi, "KPI Anomaly Detector", "Python", "Baseline deviation")
        Container(dlqc, "DLQ Cluster Analyzer", "Python", "Failure clustering")
        Container(cap, "Capacity Forecast", "Python", "Completion prediction")
        Container(log, "Log Normalizer", "Python", "Fact extraction")
    }

    Container_Boundary(infra, "Infrastructure") {
        ContainerDb(pg, "PostgreSQL 16", "Relational", "Trade state, risk, EOD")
        ContainerDb(mongo, "MongoDB 7", "Document", "Audit history")
        ContainerDb(redis, "Redis 7", "Cache", "Idempotency, session")
        ContainerDb(neo4j, "Neo4j 5", "Graph", "Dependencies, blast radius")
        ContainerDb(pgvec, "pgvector", "Vector", "Embeddings, recall")
        Container(kafka, "Kafka 3.x", "Streaming", "Domain events")
        Container(n8n, "n8n", "Agent Platform", "Workflow execution")
    }

    Rel(user, admin, "HTTPS")
    Rel(user, desk, "HTTPS")
    Rel(user, blotter, "HTTPS")
    Rel(admin, middleware, "REST API")
    Rel(agents, middleware, "MCP Tools")
    Rel(sidecars, agents, "Webhook POST")
    Rel(middleware, kafka, "Produce/Consume")
    Rel(middleware, pg, "JDBC")
    Rel(middleware, mongo, "MongoDB driver")
    Rel(middleware, redis, "Lettuce")
```
