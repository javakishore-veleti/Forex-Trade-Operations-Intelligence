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
