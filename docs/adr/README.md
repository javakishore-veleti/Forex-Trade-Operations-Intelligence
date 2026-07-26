# Architecture Decision Records (ADRs)

This directory contains the Architecture Decision Records for the FX Trade Operations Intelligence platform.

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-monorepo-language-boundaries.md) | Monorepo Language Boundaries | Accepted |
| [0002](0002-spec-driven-development.md) | Spec-Driven Development Methodology | Accepted |
| [0003](0003-golden-path-nfr-inheritance.md) | Golden Path NFR Inheritance | Accepted |
| [0004](0004-determinism-llm-boundary.md) | Determinism and LLM Boundary | Accepted |
| [0005](0005-event-driven-architecture.md) | Event-Driven Architecture with Kafka | Accepted |
| [0006](0006-mcp-tool-protocol.md) | MCP Tool Protocol for Agent-Service Communication | Accepted |
| [0007](0007-multi-tool-ai-strategy.md) | Multi-Tool AI Development Strategy | Accepted |
| [0008](0008-shared-domain-contracts-java-records.md) | Shared Domain Contracts as Java Records | Accepted |
| [0009](0009-agent-supervisor-routing.md) | Agent Supervisor Routing Strategy | Accepted |
| [0010](0010-agent-hitl-gate-pattern.md) | Agent Human-in-the-Loop (HITL) Gate Pattern | Accepted |
| [0011](0011-agent-memory-strategy.md) | Agent Memory Strategy | Accepted |
| [0012](0012-agent-model-tier-allocation.md) | Agent Model Tier Allocation | Accepted |
| [0013](0013-agent-tool-boundary.md) | Agent Tool Boundary — MCP Protocol | Accepted |
| [0014](0014-agent-risk-classification-enforcement.md) | Agent Risk Classification Enforcement | Accepted |
| [0015](0015-agent-error-handling.md) | Agent Error Handling Strategy | Accepted |
| [0016](0016-agent-evaluation-strategy.md) | Agent Evaluation Strategy | Accepted |
| [0017](0017-sidecar-to-agent-communication.md) | Sidecar-to-Agent Communication | Accepted |
| [0018](0018-agent-permitted-action-catalogue.md) | Agent Permitted-Action Catalogue | Accepted |
| [0019](0019-aws-eks-karpenter.md) | AWS EKS Node Strategy — Karpenter Autoscaling | Accepted |
| [0020](0020-aws-aurora-postgresql.md) | AWS Aurora PostgreSQL over Standard RDS | Accepted |
| [0021](0021-aws-msk-kafka.md) | AWS MSK (Managed Streaming for Kafka) | Accepted |
| [0022](0022-aws-elasticache-cluster.md) | AWS ElastiCache Cluster Mode for Idempotency | Accepted |
| [0023](0023-aws-neo4j-over-neptune.md) | Neo4j on EKS over Amazon Neptune | Accepted |
| [0024](0024-azure-aks-for-microservices.md) | Azure AKS over Container Apps | Accepted |
| [0025](0025-azure-event-hubs-kafka-mode.md) | Azure Event Hubs Kafka Protocol | Accepted |
| [0026](0026-azure-cosmos-db-mongodb.md) | Azure Cosmos DB MongoDB API | Accepted |
| [0027](0027-azure-cache-for-redis.md) | Azure Cache for Redis | Accepted |
| [0028](0028-azure-monitor-observability.md) | Azure Monitor Observability Stack | Accepted |
| [0002](0002-spec-driven-development.md) | Spec-Driven Development Methodology | Accepted |
| [0003](0003-golden-path-nfr-inheritance.md) | Golden Path NFR Inheritance | Accepted |
| [0004](0004-determinism-llm-boundary.md) | Determinism and LLM Boundary | Accepted |
| [0005](0005-event-driven-architecture.md) | Event-Driven Architecture with Kafka | Accepted |
| [0006](0006-mcp-tool-protocol.md) | MCP Tool Protocol for Agent-Service Communication | Accepted |
| [0007](0007-multi-tool-ai-strategy.md) | Multi-Tool AI Development Strategy | Accepted |
| [0008](0008-shared-domain-contracts-java-records.md) | Java Records for Immutable Domain Types | Accepted |
| [0009](0009-trade-ingest-postgresql-sequence.md) | PostgreSQL Sequence for Trade ID Generation | Accepted |
| [0010](0010-trade-lifecycle-static-transition-table.md) | Static Transition Table for Trade Lifecycle State Machine | Accepted |
| [0011](0011-risk-calculation-drools-stateless.md) | Drools StatelessKieSession per Risk Calculation | Accepted |
| [0012](0012-business-calendar-immutable-registry.md) | Immutable In-Memory CalendarRegistry at Startup | Accepted |
| [0013](0013-eod-processing-pure-readiness-evaluator.md) | Pure Function ReadinessEvaluator for EOD Processing | Accepted |
| [0014](0014-state-reconciliation-event-history.md) | Canonical State from Event History | Accepted |
| [0015](0015-kafka-topic-per-aggregate.md) | Kafka Topic per Domain Aggregate | Accepted |
| [0016](0016-event-sequence-kafka-streams.md) | Kafka Streams for Event Sequence Processor | Accepted |
| [0017](0017-dlq-quarantine-headers.md) | DLQ with Quarantine Headers | Accepted |
| [0018](0018-schema-registry-backward-compat.md) | Schema Registry BACKWARD Compatibility | Accepted |
| [0019](0019-three-separate-angular-apps.md) | Three Separate Angular Apps | Accepted |
| [0020](0020-polling-for-realtime-data.md) | Polling for Real-Time Data | Accepted |
| [0021](0021-otel-auto-instrumentation.md) | OTel Auto-Instrumentation Agent | Accepted |
| [0022](0022-structured-json-logs.md) | Structured JSON Logs | Accepted |
| [0023](0023-spring-ai-mcp-server.md) | Spring AI MCP Server for Tool Exposure | Accepted |
| [0024](0024-python-sidecars-webhook-triggers.md) | Python Sidecars as Webhook Triggers | Accepted |

## Format

Each ADR follows the structure:

1. **Title** — Short descriptive name
2. **Status** — Proposed / Accepted / Deprecated / Superseded
3. **Context** — Problem or situation prompting the decision
4. **Decision** — What was decided
5. **Consequences** — Resulting trade-offs and implications

## Adding a New ADR

1. Copy the template format from an existing ADR
2. Use the next sequential number (e.g., `0002-*.md`)
3. Submit via PR for team review

| [0030](0030-vector-db-schema-index-management.md) | Vector DB Schema and Index Management |
| [0031](0031-vector-db-embedding-lifecycle.md) | Embedding Lifecycle — Generation, Storage, Invalidation |
| [0032](0032-agent-evaluation-strategy.md) | Agent Evaluation Strategy (Evals) |
| [0033](0033-agentic-framework-choice.md) | Agentic AI Framework — n8n vs LangChain vs CrewAI |
| [0034](0034-agentic-cloud-vs-local-deployment.md) | Agent Deployment — Local-First vs Cloud-Native |
| [0035](0035-agentic-memory-architecture.md) | Agent Memory Architecture — Session, Episodic, Semantic |
| [0036](0036-terraform-over-cloudformation.md) | Terraform over CloudFormation for IaC |
