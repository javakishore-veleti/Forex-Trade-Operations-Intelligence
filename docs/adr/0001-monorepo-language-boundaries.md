# ADR-0001: Monorepo Language Boundaries

**Status:** Accepted

**Date:** 2024-01-15

## Context

The FX Trade Operations Intelligence platform requires multiple technology stacks to address different concerns:

- **Business services** need strong typing, enterprise library support, and mature observability tooling for trade lifecycle management (e.g., `trade-ingest-service`, `trade-lifecycle-service`, `risk-calculation-service`).
- **Agent orchestration** needs a visual workflow engine with built-in integrations for LLM providers, tool calling, and human-in-the-loop patterns.
- **Detection sidecars** need fast prototyping with scientific computing libraries for statistical analysis (anomaly detection, clustering, forecasting).

Mixing languages within a single tier (e.g., Python microservices alongside Java ones) would fragment tooling, increase operational complexity, and blur ownership boundaries.

## Decision

We adopt strict language-tier boundaries within a single monorepo:

| Tier | Language/Platform | Directory | Responsibility |
|------|-------------------|-----------|----------------|
| Middleware | Java 21 / Spring Boot | `Middleware/` | All business logic, API endpoints, trade processing, state management |
| Portals | TypeScript / Angular 19 | `Portals/` | All user-facing web applications |
| Agents | n8n (JSON workflow exports) | `Agents/` | AI agent orchestration, tool calling, workflow coordination |
| Sidecars | Python 3.11+ | `Sidecars/` | Statistical detection, embedding, log normalization — **no business logic** |

### Key Constraints

1. **Python sidecars never contain business logic.** They perform detection/analysis and return results via MCP_Tool_Contract-compatible JSON envelopes. Trade decisions, state mutations, and persistence are always in Java.

2. **n8n workflows are export-only artifacts.** The `Agents/` directory contains JSON exports for import into the n8n runtime — no application code.

3. **No cross-tier language mixing.** A Java service does not embed Python. A sidecar does not import Spring libraries. Portals do not run server-side Java.

4. **Communication between tiers** uses well-defined contracts:
   - Middleware ↔ Portals: REST/WebSocket APIs
   - Middleware ↔ Sidecars: MCP_Tool_Contract JSON over HTTP
   - Agents ↔ Middleware: n8n HTTP/tool nodes calling service endpoints
   - Services ↔ Services: Kafka events (e.g., `state-reconciliation-service`)

## Consequences

### Positive

- Clear ownership: each team owns one language/build system
- Independent scaling of build/test pipelines per tier
- Simplified dependency management (Maven for Java, npm for TS, pip/hatch for Python)
- Reduced cognitive load — developers work in one stack per PR

### Negative

- Contract drift risk between tiers requires explicit schema governance
- Local development requires multiple runtimes (JDK 21, Node LTS, Python 3.11+, Docker)
- Integration testing spans language boundaries and needs dedicated compose environments

### Mitigations

- `shared-domain-contracts` module in Middleware publishes canonical DTOs
- Docker Compose local stack (`DevOps/Local/`) provides all infrastructure dependencies
- CI validates contracts at PR time (planned)

## Examples

- Trade `FX-000001` is ingested by `trade-ingest-service` (Java), anomaly-scored by `kpi-anomaly-detector` (Python sidecar), and the result is routed by the supervisor agent workflow (n8n) to `risk-calculation-service` (Java).
- The `log-normalizer` sidecar (Python) processes raw logs but never mutates trade state — it returns normalized output for Elasticsearch indexing.
