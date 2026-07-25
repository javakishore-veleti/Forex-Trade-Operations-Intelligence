# Kiro Understanding — Spec-Driven Development Workflow

This document captures Kiro's guidance on how this project progresses from specification to
production, and the build sequence it recommends. It is the shared understanding between Kiro,
Claude Code, and the developer for how work proceeds.

## The three-document progression (per feature)

In the Kiro workflow, every feature/spec advances through three documents, then code:

```
requirements.md  →  design.md  →  tasks.md  →  code
```

- **`requirements.md`** — the *what* and *why*. Feature requirements, acceptance criteria
  (EARS style), success metrics. In this repo these are **technology-agnostic** (reference
  Technology Roles) and **business/domain-focused** (each microservice is a DDD bounded context
  inheriting cross-cutting NFRs from the architecture golden path).
- **`design.md`** — the technical design that satisfies those requirements:
  - Class/module structure, API contracts, DB schema, Kafka topic/event shapes
  - Sequence diagrams for key flows
  - Data-model decisions, error-handling strategy
  - How the service inherits and applies the golden-path NFRs
  - This is where **Technology Roles resolve to concrete products** (the agnostic → concrete boundary).
- **`tasks.md`** — the concrete implementation checklist derived from the design:
  - Ordered, atomic tasks an agent (or developer) can execute one at a time
  - Each task maps to a specific file or unit of work
  - Tasks scoped so each one can be verified independently

Once `tasks.md` exists for a spec, you **execute** it — generating the code, running the build,
running tests, and iterating until all tasks are checked off.

Current state: `requirements.md` is done for 10 specs; `design.md` started with
`02-microservices/03-trade-lifecycle-service` (reference example). The remaining two documents
per spec are still to be produced.

## The build sequence for this project

Given the build order defined in `MASTER-PLAN.md`, the natural next steps in order are:

1. **Write `design.md` + `tasks.md` for the 7 microservices (Phase 02)** — these are the
   foundation everything else depends on.
2. **Generate the microservice code** — scaffold, implement, and test each service.
3. **Phase 03 — Events** — Kafka topics, domain-event schemas, sequence processor, DLQ.
4. **Phase 04 — Portals** — the three Angular apps.
5. **Phase 05 — Observability** — OpenTelemetry instrumentation across all services.
6. **Phase 06 — Local deploy** — wire the MCP server, Python sidecars, and n8n locally. This is
   the first time an agent can run end-to-end.
7. **Phase 07 — Agents** — implement the 34 n8n workflow JSONs.
8. **Phase 08 / 09 — Cloud deploy** — AWS or Azure.

## The critical dependency

Phases 06 and 07 (agents) **cannot** be built until the Spring Boot services (02), events (03),
and local-deploy infrastructure (06) exist — because agents call MCP tools that are backed by
those services.

## In short

> The spec work is the blueprint. Implementation is next.
