# Documentation

## Table of Contents

### Architecture Decision Records
| ADR | Title |
|-----|-------|
| [0001](adr/0001-monorepo-language-boundaries.md) | Monorepo Language Boundaries |
| [0002](adr/0002-spec-driven-development.md) | Spec-Driven Development Methodology |
| [0003](adr/0003-golden-path-nfr-inheritance.md) | Golden Path NFR Inheritance |
| [0004](adr/0004-determinism-llm-boundary.md) | Determinism and LLM Boundary |
| [0005](adr/0005-event-driven-architecture.md) | Event-Driven Architecture with Kafka |
| [0006](adr/0006-mcp-tool-protocol.md) | MCP Tool Protocol for Agent-Service Communication |
| [0007](adr/0007-multi-tool-ai-strategy.md) | Multi-Tool AI Development Strategy |
| [0008](adr/0008-shared-domain-contracts-java-records.md) | Shared Domain Contracts as Java Records |
| [0009](adr/0009-agent-supervisor-routing.md) | Agent Supervisor Routing Strategy |
| [0010](adr/0010-agent-hitl-gate-pattern.md) | Agent HITL Gate Pattern |
| [0011](adr/0011-agent-memory-strategy.md) | Agent Memory Strategy |
| [0012](adr/0012-agent-model-tier-allocation.md) | Agent Model Tier Allocation |
| [0013](adr/0013-agent-tool-boundary.md) | Agent Tool Boundary — MCP Protocol |
| [0014](adr/0014-agent-risk-classification-enforcement.md) | Agent Risk Classification Enforcement |
| [0015](adr/0015-agent-error-handling.md) | Agent Error Handling Strategy |
| [0016](adr/0016-agent-evaluation-strategy.md) | Agent Evaluation Strategy |
| [0017](adr/0017-sidecar-to-agent-communication.md) | Sidecar-to-Agent Communication |
| [0018](adr/0018-agent-permitted-action-catalogue.md) | Agent Permitted-Action Catalogue |
| [0019](adr/0019-aws-eks-karpenter.md) | AWS EKS — Karpenter Autoscaling |
| [0020](adr/0020-aws-aurora-postgresql.md) | AWS Aurora PostgreSQL |
| [0021](adr/0021-aws-msk-kafka.md) | AWS MSK for Kafka |
| [0022](adr/0022-aws-elasticache-cluster.md) | AWS ElastiCache Cluster Mode |
| [0023](adr/0023-aws-neo4j-over-neptune.md) | Neo4j on EKS over Neptune |
| [0024](adr/0024-azure-aks-for-microservices.md) | Azure AKS for Microservices |
| [0025](adr/0025-azure-event-hubs-kafka-mode.md) | Azure Event Hubs Kafka Protocol |
| [0026](adr/0026-azure-cosmos-db-mongodb.md) | Azure Cosmos DB MongoDB API |
| [0027](adr/0027-azure-cache-for-redis.md) | Azure Cache for Redis |
| [0028](adr/0028-azure-monitor-observability.md) | Azure Monitor Observability Stack |
| [0008](adr/0008-shared-domain-contracts-java-records.md) | Java Records for Immutable Domain Types |
| [0009](adr/0009-trade-ingest-postgresql-sequence.md) | PostgreSQL Sequence for Trade ID Generation |
| [0010](adr/0010-trade-lifecycle-static-transition-table.md) | Static Transition Table for Trade Lifecycle State Machine |
| [0011](adr/0011-risk-calculation-drools-stateless.md) | Drools StatelessKieSession per Risk Calculation |
| [0012](adr/0012-business-calendar-immutable-registry.md) | Immutable In-Memory CalendarRegistry at Startup |
| [0013](adr/0013-eod-processing-pure-readiness-evaluator.md) | Pure Function ReadinessEvaluator for EOD Processing |
| [0014](adr/0014-state-reconciliation-event-history.md) | Canonical State from Event History |
| [0015](adr/0015-kafka-topic-per-aggregate.md) | Kafka Topic per Domain Aggregate |
| [0016](adr/0016-event-sequence-kafka-streams.md) | Kafka Streams for Event Sequence Processor |
| [0017](adr/0017-dlq-quarantine-headers.md) | DLQ with Quarantine Headers |
| [0018](adr/0018-schema-registry-backward-compat.md) | Schema Registry BACKWARD Compatibility |
| [0019](adr/0019-three-separate-angular-apps.md) | Three Separate Angular Apps |
| [0020](adr/0020-polling-for-realtime-data.md) | Polling for Real-Time Data |
| [0021](adr/0021-otel-auto-instrumentation.md) | OTel Auto-Instrumentation Agent |
| [0022](adr/0022-structured-json-logs.md) | Structured JSON Logs |
| [0023](adr/0023-spring-ai-mcp-server.md) | Spring AI MCP Server for Tool Exposure |
| [0024](adr/0024-python-sidecars-webhook-triggers.md) | Python Sidecars as Webhook Triggers |

### Observability
| Document | Description |
|----------|-------------|
| [span-naming.md](observability/span-naming.md) | OpenTelemetry span naming conventions |
| [kafka-tracing.md](observability/kafka-tracing.md) | W3C TraceContext propagation through Kafka |
| [structured-logging.md](observability/structured-logging.md) | JSON structured log format specification |

### Events
| Document | Description |
|----------|-------------|
| [schema-catalogue.md](events/schema-catalogue.md) | Domain event schema catalogue (all event types) |

### Diagrams
See [diagrams/README.md](diagrams/README.md) for diagramming conventions.

### Use Cases
| Document | Description |
|----------|-------------|
| [use-cases.md](use-cases.md) | 50 platform use cases with personas, agent interactions, and HITL gates |

### Key Design Documents (repo root)
| Document | Description |
|----------|-------------|
| [README.md](../README.md) | Project overview and quick start |
| [Kiro-Understanding.md](../Kiro-Understanding.md) | Kiro SDD methodology documentation |
| [PRD.md](../PRD.md) | Product Requirements Document |
| [runtime_agents_catalog.md](../runtime_agents_catalog.md) | 34-agent master catalog |
| [CONTRIBUTING.md](../CONTRIBUTING.md) | Contribution guidelines |
| [CLAUDE.md](../CLAUDE.md) | Claude Code guidance file |
