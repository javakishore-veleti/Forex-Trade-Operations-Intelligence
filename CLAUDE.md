# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

This is a **reference-architecture repository currently in its spec / design phase** — it is *intended to become a buildable multi-service implementation*, not to stay documentation-only. As of now it contains only Markdown/Word specs (no build/test/lint tooling yet), but the target is a **monorepo** of: Spring Boot (Java/Maven) microservices exposing **Spring AI MCP server** tools, n8n agent workflows, Python agentic sidecars (detection/embeddings), and **manually-triggered GitHub Actions** deploying each service independently to local + AWS/Azure. The docs are the spec that code will be built against (spec-driven development). When scaffolding code, do so freely — don't treat this as a read-only doc repo.

The substance today lives in Markdown design documents:

- `forex_trade_operations_intelligence_public.md` — the canonical framework doc (~1200 lines): the 16 original agents, the model portfolio, the tool-envelope contract, "where n8n fits." Edit this when changing the framework.
- `forex_trade_operations_intelligence_public.docx` — a Word export of the framework doc (adds a ToC). Treat as a generated derivative; regenerate after `.md` edits or it goes stale.
- `runtime_agents_catalog.md` — the **master agent catalog**: 34 deduplicated agent concepts from four AI sources (ChatGPT/Claude/Copilot/Gemini), each with runtime signal, sources, Spring AI MCP tools to build, Python sidecar, model tiers, design-pattern tags, risk level, and a phased build order. This is the build backlog.

The `.gitignore` is a stock Java template (`*.class`, `*.jar`, BlueJ files) — it predates any actual code and does not imply a Java build exists here.

## Subject matter

The document specifies a **runtime-intelligence multi-agent platform for Forex trade operations**, orchestrated with **n8n**. The core thesis, which should shape any code written against this spec:

- **Language boundary (hard rule):** all **microservices / trading business logic are Spring Boot (Java, Maven) only** — trade ingest, event processing, risk calculation, EOD, rules execution, transactional/stateful services, and every agent tool endpoint. **Python is permitted ONLY in the agentic/AI layer** (n8n custom logic, ML/statistical detectors, embeddings, analytics glue that feed the agents) — never for trade/business/transactional logic. Do not introduce FastAPI/Flask/Django microservices; treat any "Spring Boot / FastAPI" phrasing in the design doc as **Spring Boot only** for the service tier.
- The agents **observe and coordinate**; they do **not** replace microservices. Trade processing, risk calculation, exact arithmetic, rules evaluation, idempotency, authN/authZ, and auditable state transitions stay in deterministic **Spring Boot** services.
- **LLMs never compute official numbers.** Risk, exposure, materiality, and canonical-state decisions come from deterministic services (e.g. `StateReconciliationService`, a Drools/policy service). The model interprets, explains, plans, and coordinates around those results.
- **Tool-exposure mechanism:** Spring Boot business services publish their capabilities as **MCP tools via Spring AI's MCP server** support. The design doc's "controlled tool endpoints" (`getTrade()`, `replayTradeEvent()`, `requestRiskRecalculation()`, …) are implemented as **Spring AI MCP server tools**, not ad-hoc REST. **n8n is the MCP client / agent host** that discovers and calls them. The design doc's agent envelope maps onto MCP typed schemas: the structured action payload → the tool `inputSchema`; the `facts / violations / permittedActions / evidence / expiresAt` envelope → the tool's structured output. The typed MCP boundary is what enforces "no natural-language-generated arbitrary payloads."
- n8n is the orchestration layer: supervisor agent → specialized subagents (Lifecycle, Risk/Rules, Recovery, Event Integrity, Data Readiness) → Spring AI MCP tools → **Spring Boot** business logic → enterprise systems (Kafka, PostgreSQL, MongoDB, Redis, Neo4j, ELK, Grafana, Databricks, Drools). The human-approval gate lives in n8n *before* invoking any sensitive MCP tool. Python detection/embedding sidecars sit beside the agents (emitting anomaly-envelope triggers), not in the service tier.
- High-volume Kafka is **never** fed directly through an LLM workflow. Stream processors (Spring Boot Kafka Streams) do continuous detection and trigger n8n with a compact anomaly envelope only on violation/threshold/request.

## Conventions the document establishes (apply these to any implementation)

- **Agent tool contract:** every tool endpoint returns an agent-friendly envelope with `requestId`, `businessEntity`, `status`, `facts`, `violations`, `permittedActions`, `evidence[]`, `dataClassification`, `expiresAt`. Action tools take a structured payload (`action`, `entityId`, `reasonCode`, `expectedVersion`, `idempotencyKey`, `approvalReference`, `dryRun`) — never natural-language-generated arbitrary payloads.
- **Narrow action APIs only.** Recovery/action tools are specific endpoints like `POST /trades/{id}/risk-recalculation-requests`. Agents must not get generic DB write access, shell access, unrestricted Kafka production, admin Kubernetes credentials, or unrestricted production Cypher.
- **Human approval gate** precedes sensitive actions (rule deployment, routing changes, global consolidation, replay). The pattern is: agent proposes → deterministic simulation → impact report → human approval → controlled service applies.
- **Model portfolio, not one model:** small extraction/classification model, statistical/time-series detector, strong reasoning LLM for explanation/planning, embedding model for prior-case retrieval, deterministic engines for all calculation and policy.

## Public-repository safeguards (hard requirement)

This is an intentionally public reference implementation using only **synthetic data and fictional organizations**. When editing docs or adding code/examples, do not introduce: real trade/counterparty/account/customer/employee/branch identifiers; employer or client names; production URLs, credentials, secrets, tokens, or internal hostnames; proprietary Kafka topics, DB schemas, rule definitions, thresholds, or market-data contracts; or real logs/payloads/diagrams/figures. All identifiers (e.g. `FX-928734`, rule `FX-REGION-APAC-042`) are illustrative placeholders — keep new examples equally generic and reproducible.
