# Kiro Understanding — Spec-Driven Development Workflow

This document captures the Kiro spec-driven development methodology demonstrated by this
project. It is the shared understanding between Kiro, Claude Code, and the developer for
how work proceeds.

## The Three-Document Progression (per feature)

Every feature/spec advances through three documents, then code:

```
requirements.md  →  design.md  →  tasks.md  →  code
```

- **`requirements.md`** — the *what* and *why*. Feature requirements, acceptance criteria,
  user stories. Technology-agnostic (references Technology Roles, not product names).
  Business/domain-focused (each microservice is a DDD bounded context inheriting cross-cutting
  NFRs from the architecture golden path).
- **`design.md`** — the technical *how*. Resolves Technology Roles to concrete products.
  Defines class/module structure, API contracts, DB schema, event shapes, sequence flows,
  golden-path NFR realization, testing strategy, and requirement traceability.
- **`tasks.md`** — the concrete implementation checklist. Ordered, atomic tasks. Each maps to
  a specific file. Each is independently verifiable. A living checklist marked `[x]` as
  tasks complete.

## Architectural Decisions (enforced by specs)

| Decision | Rule |
|---|---|
| Tech single-source-of-truth | `01-initial-setup/01-technology-stack` owns all product+version bindings |
| NFR single-source-of-truth | `architecture-golden-path/01-service-nfrs` owns all cross-cutting NFRs |
| Agnostic specs | requirements.md references Technology Roles, never product names |
| Concrete in design | design.md is where roles resolve to products |
| Golden-path inheritance | Every microservice inherits GP-Rq-1..14; restates only business requirements |
| DDD bounded contexts | Each service is its own bounded context with ubiquitous language |
| Shared kernel | `shared-domain-contracts` provides the shared types (not a running service) |

## Current Project State (2026-07-25)

| Phase | Specs | Req | Design | Tasks | Code |
|---|---|---|---|---|---|
| 01-initial-setup | 2 | ✅ | ✅ | ✅ | ✅ |
| architecture-golden-path | 1 | ✅ | n/a | n/a | n/a |
| 02-microservices | 7 | ✅ | ✅ | ✅ | ✅ (346 tests) |
| 03-events | 4 | ✅ | ✅ | ✅ | ✅ (95 tests) |
| 04-portals | 3 | ✅ | ✅ | ✅ | ✅ |
| 05-observability | 4 | ✅ | ✅ | ✅ | ✅ |
| 06-local-deploy | 3 | ✅ | ✅ | ✅ | ✅ |
| 07-n8n-agents | 34 | ✅ | ✅ | ✅ | 4/34 (MVP) |
| 08-aws-deploy | 7 | ⬜ | ⬜ | ⬜ | ⬜ |
| 09-azure-deploy | 6 | ⬜ | ⬜ | ⬜ | ⬜ |

## Build Order (realized)

1. ✅ **Shared Domain Contracts** — compile-scope library, no runtime framework
2. ✅ **Trade Ingest Service** — REST capture, validation, idempotency, Kafka publish
3. ✅ **Trade Lifecycle Service** — state machine, Kafka consumer, MongoDB audit
4. ✅ **Risk Calculation Service** — Drools, BigDecimal arithmetic, aggregations
5. ✅ **Business Calendar Service** — DST-aware java.time, immutable CalendarRegistry
6. ✅ **EOD Processing Service** — close orchestration, pure ReadinessEvaluator
7. ✅ **State Reconciliation Service** — read-only canonical-state authority
8. ✅ **Events** — topic registry, event model, sequence processor, DLQ
9. ✅ **Portals** — 3 Angular apps with 13 feature views
10. ✅ **Observability** — OTel tracing, Prometheus metrics, Grafana dashboards, ELK
11. ✅ **Local Deploy** — MCP server layer, Python sidecars, n8n wiring
12. 🔄 **n8n Agents** — 4/34 implemented (supervisor, trade-lifecycle, canary-probe, dlq-triage)

## Kiro Hooks

| Hook | Purpose | Status |
|---|---|---|
| `auto-commit-specs` | Git-commit every spec file save | ✅ active |
| `validate-spec-agnostic` | Warn on product names in requirements | ✅ active |
| `guard-no-secrets` | Warn on secrets in implementation files | ✅ active |
| `sync-spec-status` | Update MASTER-PLAN on design/tasks creation | ⬜ defined |
| `update-master-plan-progress` | Mark code complete when all tasks ticked | ⬜ defined |

## Tools Used

- **Kiro CLI** — spec management, quality audit, status tracking, implementation
- **Claude Code CLI** — bulk code generation, parallel spec authoring
- **Kiro IDE** — visual spec navigation, sub-agent task execution

All three tools read the same `.kiro/specs/` files and produce the same standard output.
The spec structure is tool-agnostic — it's plain markdown files in Git.
