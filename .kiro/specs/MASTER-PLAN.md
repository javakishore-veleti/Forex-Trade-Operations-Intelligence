# Forex Trade Operations Intelligence — Master Spec Plan

> This document captures the full spec hierarchy, build order, and architectural
> decisions agreed during initial planning. Update this file as new specs are added
> or the build order changes. Do not delete completed entries — mark them ✅.

---

## Architectural Decisions (Non-Negotiable)

| Decision | Rule |
|---|---|
| Microservices language | Spring Boot (Java/Maven) ONLY — all business/transactional logic |
| Agent platform | n8n ONLY — all AI agents are n8n workflow JSON exports |
| Python scope | Detection/embedding sidecars ONLY — no agents, no business logic |
| LLM boundary | LLMs never compute official numbers — deterministic services do |
| Kafka boundary | High-volume Kafka never flows through n8n — stream processors detect, then trigger n8n with compact envelope |
| Action gate | Every M/H-risk action: propose → deterministic simulation → impact report → human approval → controlled MCP tool executes |
| Build order | Business app → events → portals → observability → local deploy → n8n agents → cloud deploy |
| Public safeguard | All identifiers synthetic only — FX- prefix for trade IDs, fictional orgs, no real credentials |
| Spec style | Requirements are **technology-agnostic** — they reference **Technology Roles** (e.g. `RELATIONAL_STORE`, `EVENT_STREAM`, `RULES_ENGINE`), never product names or versions |
| Tech single-source-of-truth | ALL technologies + versions declared only in `01-initial-setup/01-technology-stack`; a tech/version change is a one-file edit |
| NFR single-source-of-truth | Cross-cutting NFRs (correlation ID, health probes, error envelope, security placeholder, event atomicity, idempotency, observability, resilience, config, testing, determinism) live once in `architecture-golden-path/01-service-nfrs`; every microservice **inherits** and restates only business/domain requirements |
| Domain modeling | Each microservice is a **DDD bounded context** expressing business/functional requirements + ubiquitous language; `shared-domain-contracts` is the **shared kernel** |

---

## Repo Root Structure

```
Forex-Trade-Operations-Intelligence/
├── Middleware/                        ← ALL Spring Boot microservices (Java/Maven)
├── Portals/
│   ├── Admin/                         ← Angular standalone app (own package.json)
│   ├── TraderDesk/                    ← Angular standalone app (own package.json)
│   └── FXTradeBlotter/                ← Angular standalone app (own package.json)
├── Agents/                            ← n8n workflow JSON exports ONLY
│   ├── workflows/supervisor/
│   ├── workflows/specialized/
│   └── workflows/utilities/
├── Sidecars/                          ← Python detection/embedding packages ONLY
├── DevOps/
│   └── Local/                         ← per-service docker-compose + orchestration scripts
├── docs/
│   ├── adr/
│   └── diagrams/
├── .github/
│   ├── workflows/
│   └── CODEOWNERS
├── scripts/
└── package.json                       ← root developer commands: start/stop/status/install
```

---

## Spec Folder Structure

```
.kiro/specs/
├── MASTER-PLAN.md
├── 01-initial-setup/
│   ├── 01-technology-stack/                  ← ✅ central tech registry (Technology Roles + versions)
│   └── 02-repo-skeleton/                     ← repo skeleton, scaffolds, DevOps/Local
│
├── architecture-golden-path/                 ← cross-cutting, inherited by every microservice
│   └── 01-service-nfrs/                      ← ✅ correlation ID, health, errors, security, eventing, testing, determinism
│
├── 02-microservices/
│   ├── 01-shared-domain-contracts/
│   ├── 02-trade-ingest-service/
│   ├── 03-trade-lifecycle-service/
│   ├── 04-risk-calculation-service/
│   ├── 05-eod-processing-service/
│   ├── 06-business-calendar-service/
│   └── 07-state-reconciliation-service/
│
├── 03-events/
│   ├── 01-kafka-topic-design/
│   ├── 02-domain-events-model/
│   ├── 03-event-sequence-processor/
│   └── 04-dlq-management/
│
├── 04-portals/
│   ├── 01-portal-admin/
│   ├── 02-portal-traderdesk/
│   └── 03-portal-fxtradeblotter/
│
├── 05-observability/
│   ├── 01-otel-spring-boot/
│   ├── 02-otel-kafka-tracing/
│   ├── 03-otel-metrics-dashboards/
│   └── 04-otel-log-correlation/
│
├── 06-local-deploy/
│   ├── 01-mcp-server-setup/
│   ├── 02-python-sidecars/
│   └── 03-n8n-local-setup/
│
├── 07-n8n-agents/
│   ├── 01-supervisor-agent/
│   ├── 02-trade-lifecycle-agent/
│   ├── 03-state-divergence-agent/
│   ├── 04-event-integrity-agent/
│   ├── 05-risk-explainability-agent/
│   ├── 06-rule-impact-agent/
│   ├── 07-rule-coverage-agent/
│   ├── 08-shadow-rule-simulator/
│   ├── 09-counterparty-exposure-agent/
│   ├── 10-eod-readiness-agent/
│   ├── 11-data-freshness-agent/
│   ├── 12-exception-materiality-agent/
│   ├── 13-settlement-fail-predictor/
│   ├── 14-databricks-lineage-agent/
│   ├── 15-regulatory-reporting-agent/
│   ├── 16-business-kpi-guard/
│   ├── 17-change-correlation-agent/
│   ├── 18-canary-probe-agent/
│   ├── 19-trace-latency-agent/
│   ├── 20-runtime-intent-agent/
│   ├── 21-dlq-triage-agent/
│   ├── 22-consumer-lag-predictor/
│   ├── 23-market-data-staleness-agent/
│   ├── 24-schema-contract-drift-agent/
│   ├── 25-cutoff-calendar-agent/
│   ├── 26-contagion-analysis-agent/
│   ├── 27-service-genome-agent/
│   ├── 28-adaptive-routing-agent/
│   ├── 29-capacity-backlog-agent/
│   ├── 30-retry-storm-agent/
│   ├── 31-finops-cost-agent/
│   ├── 32-trade-amendment-ripple-agent/
│   ├── 33-duplicate-effect-guard/
│   └── 34-transaction-recovery-agent/
│
├── 08-aws-deploy/
│   ├── 01-eks-cluster/
│   ├── 02-rds-postgres/
│   ├── 03-msk-kafka/
│   ├── 04-elasticache-redis/
│   ├── 05-documentdb-mongodb/
│   ├── 06-neptune-neo4j/
│   └── 07-opensearch-elk/
│
└── 09-azure-deploy/
    ├── 01-aks-cluster/
    ├── 02-azure-postgres/
    ├── 03-azure-event-hub/
    ├── 04-azure-cache-redis/
    ├── 05-cosmos-mongodb/
    └── 06-azure-monitor-elk/
```

---

## Full Spec Inventory

### 01 — Initial Setup

| Spec | Status | Scope |
|---|---|---|
| `01-initial-setup/01-technology-stack` | ✅ Requirements done | **Single source of truth** for all technologies + versions. Defines Technology Roles (`RELATIONAL_STORE`, `DOCUMENT_STORE`, `CACHE`, `GRAPH_STORE`, `EVENT_STREAM`, `RULES_ENGINE`, `SERVICE_LANGUAGE`, `AGENT_PLATFORM`, `AGENT_TOOL_PROTOCOL`, cloud target bindings, etc.). Every other spec references these roles and stays product/version agnostic. |
| `01-initial-setup/02-repo-skeleton` | ✅ Requirements + Design + Tasks done | Repo skeleton, all top-level folders, service/portal/sidecar scaffolds, DevOps/Local compose (9 services), orchestration scripts, root package.json, tooling files, docs/adr. |

---

### Architecture Golden Path (cross-cutting — inherited by every microservice)

| Spec | Status | Scope |
|---|---|---|
| `architecture-golden-path/01-service-nfrs` | ✅ Requirements done | **Single source of truth for cross-cutting NFRs.** API conventions & versioning, correlation-ID propagation, structured error envelopes, health/readiness probes, idempotency, optimistic locking, event-publish atomicity & consumption, observability, security placeholder, downstream resilience, configuration/profiles, testing standards, determinism/LLM boundary, synthetic-data safeguard. Every microservice inherits these (GP-Rq-1..14) and restates only business/domain requirements. |

---

### 02 — Microservices

Each is a standalone Spring Boot Maven module under `Middleware/`. Built as plain REST + Kafka + JPA — no MCP, no Spring AI until Phase 06-local-deploy.

| Spec | Status | Scope |
|---|---|---|
| `02-microservices/01-shared-domain-contracts` | ✅ Requirements ✅ Design ✅ Tasks ✅ Implemented | Shared Java library: Trade, TradeEvent, RiskResult, Region, TradingBook, Counterparty, Money, AuditInfo DTOs + all enums (TradeStatus, TradeEventType, RegionCode, RiskLevel etc.) |
| `02-microservices/02-trade-ingest-service` | ✅ Requirements ✅ Design ✅ Tasks ✅ Implemented | REST trade capture endpoint, input validation, idempotency check, Postgres write, publishes TradeCaptured event to Kafka |
| `02-microservices/03-trade-lifecycle-service` | ✅ Requirements ✅ Design ✅ Tasks ✅ Implemented | Trade state machine (CAPTURED→SETTLED), lifecycle transition rules, audit history, Postgres + MongoDB read/write, state query APIs |
| `02-microservices/04-risk-calculation-service` | ✅ Requirements done | Currency pair risk calculation, Drools rules engine integration, regional/book/global aggregation, EOD risk totals, limit checking, RiskCalculationCompleted event |
| `02-microservices/05-eod-processing-service` | ✅ Requirements ✅ Design ✅ Tasks ✅ Implemented | EOD regional close orchestration, branch completion tracking, global consolidation trigger, US base calendar alignment |
| `02-microservices/06-business-calendar-service` | ✅ Requirements ✅ Design ✅ Tasks ✅ Implemented | Regional business calendars, DST/holiday rules, booking date classification, regional cutoff enforcement |
| `02-microservices/07-state-reconciliation-service` | ✅ Requirements ✅ Design ✅ Tasks ✅ Implemented | Cross-system canonical state (Postgres + MongoDB + Redis + Kafka), divergence detection, StateReconciliationService returning violatedInvariants + permittedActions |

---

### 03 — Events & Messaging

| Spec | Status | Scope |
|---|---|---|
| `03-events/01-kafka-topic-design` | ✅ Requirements done | Topic naming conventions, partition strategy, replication factor, schema registry setup, retention policy per topic |
| `03-events/02-domain-events-model` | ✅ Requirements done | Full event schema catalogue: TradeCaptured → TradeSettled + amendment/cancellation/replay events, field-level documentation |
| `03-events/03-event-sequence-processor` | ✅ Requirements done | STREAM_PROCESSING processor maintaining per-tradeId sequence facts {observedEvents, missingEvents, duplicates, sequenceViolations}, emits anomaly envelope on violation |
| `03-events/04-dlq-management` | ✅ Requirements done | DLQ topic naming, retry policy per topic, poison message quarantine strategy, DLQ consumer monitoring |

---

### 04 — Portals

Each is a standalone Angular app under `Portals/` with its own package.json.

| Spec | Status | Scope |
|---|---|---|
| `04-portals/01-portal-admin` | ✅ Requirements done | Ops/risk admin portal — trade investigation, EOD status dashboard, risk aggregation views, exception management, n8n approval workflow UI integration |
| `04-portals/02-portal-traderdesk` | ✅ Requirements done | Customer trader portal — trade lifecycle status, risk explanation views, position summary, trading book view |
| `04-portals/03-portal-fxtradeblotter` | ✅ Requirements done | Broker blotter — real-time position, exposure, settlement status, counterparty exposure |

---

### 05 — Observability

Instrument the running application. No new business logic.

| Spec | Status | Scope |
|---|---|---|
| `05-observability/01-otel-spring-boot` | ✅ Requirements done | OTel auto-instrumentation for all Middleware services, span naming, W3C TraceContext propagation, tradeId/region baggage |
| `05-observability/02-otel-kafka-tracing` | ✅ Requirements done | W3C trace context through Kafka headers, producer/consumer span correlation |
| `05-observability/03-otel-metrics-dashboards` | ✅ Requirements done | Grafana dashboards per service, Prometheus scrape config, alert rules for trade throughput/risk latency/EOD completion |
| `05-observability/04-otel-log-correlation` | ✅ Requirements done | ELK pipeline, structured logging with traceId/spanId/tradeId, Logstash pipelines, Kibana index patterns |

---

### 06 — Local Deploy (MCP + Sidecars + n8n wiring)

| Spec | Status | Scope |
|---|---|---|
| `06-local-deploy/01-mcp-server-setup` | ✅ Requirements done | Add Spring AI MCP server to Middleware services, shared-mcp-contracts library, agent envelope DTOs, tool registration, local MCP gateway config |
| `06-local-deploy/02-python-sidecars` | ✅ Requirements done | Build and run 4 Python detection sidecars locally (kpi-anomaly-detector, dlq-cluster-analyzer, capacity-forecast-model, log-normalizer), sidecar → n8n webhook trigger wiring |
| `06-local-deploy/03-n8n-local-setup` | ✅ Requirements done | n8n local instance config, import workflow JSONs, MCP client credentials, webhook endpoints, test end-to-end tool call from n8n → Spring Boot MCP |

---

### 07 — n8n Agents

All implemented as n8n workflow JSON exports only. No Python agent scripts.

| Spec | Status | Scope |
|---|---|---|
| `07-n8n-agents/01-supervisor-agent` | ✅ Requirements done | Intent classification, sub-agent routing, session memory, multi-turn conversation |
| `07-n8n-agents/02-trade-lifecycle-agent` | ✅ Requirements done | Timeline reconstruction, gap/duplicate detection, probable cause, safe action |
| `07-n8n-agents/34-state-divergence-agent` | ✅ Requirements ✅ Design ✅ Tasks ✅ Implemented | Cross-system state comparison, HITL reconciliation |
| `07-n8n-agents/04-event-integrity-agent` | ⬜ Not started | Sequence violation handling, quarantine/pause/replay |
| `07-n8n-agents/05-risk-explainability-agent` | ✅ Requirements ✅ Design ✅ Tasks | Multi-factor risk explanation, rule-trace readable, follow-up Q&A |
| `07-n8n-agents/06-rule-impact-agent` | ✅ Requirements ✅ Design ✅ Tasks | Drools firing anomaly, pre/post deploy comparison, rollback gate |
| `07-n8n-agents/07-rule-coverage-agent` | ✅ Requirements ✅ Design ✅ Tasks | Currency-pair coverage matrix, fallback firing rate, uncovered pair detection |
| `07-n8n-agents/08-shadow-rule-simulator` | ✅ Requirements ✅ Design ✅ Tasks | NL→DRL, shadow pod replay, impact diff, HITL deploy gate |
| `07-n8n-agents/09-counterparty-exposure-agent` | ✅ Requirements ✅ Design ✅ Tasks | Live exposure narrative, Neo4j traversal, limit comparison |
| `07-n8n-agents/10-eod-readiness-agent` | ✅ Requirements ✅ Design ✅ Tasks | Regional sub-agents + global supervisor, go/no-go, HITL exception approval |
| `07-n8n-agents/11-data-freshness-agent` | ✅ Requirements ✅ Design ✅ Tasks | Pre-process freshness gate, BLOCK/ACCEPT per dataset |
| `07-n8n-agents/12-exception-materiality-agent` | ✅ Requirements ✅ Design ✅ Tasks | Materiality classification, global-close blockers vs tolerable |
| `07-n8n-agents/13-settlement-fail-predictor` | ✅ Requirements ✅ Design ✅ Tasks | Pre-settlement risk, nostro shortfall, missing SSI |
| `07-n8n-agents/14-databricks-lineage-agent` | ✅ Requirements ✅ Design ✅ Tasks | Unity Catalog lineage, job failure impact, aggregation gate |
| `07-n8n-agents/15-regulatory-reporting-agent` | ✅ Requirements ✅ Design ✅ Tasks | Completeness attestation, gap detection, resubmit gate |
| `07-n8n-agents/16-business-kpi-guard` | ✅ Requirements ✅ Design ✅ Tasks | KPI anomaly, calendar-aware baseline, LLM only after anomaly |
| `07-n8n-agents/17-change-correlation-agent` | ✅ Requirements ✅ Design ✅ Tasks | Deploys + rule changes correlated to business outcome shifts |
| `07-n8n-agents/18-canary-probe-agent` | ✅ Requirements ✅ Design ✅ Tasks | Synthetic trade injection, per-stage liveness assertion |
| `07-n8n-agents/19-trace-latency-agent` | ✅ Requirements ✅ Design ✅ Tasks | OTel per-stage latency, SLA breach root cause |
| `07-n8n-agents/20-runtime-intent-agent` | ✅ Requirements ✅ Design ✅ Tasks | Behavioral clustering, suppress false alarms |
| `07-n8n-agents/21-dlq-triage-agent` | ✅ Requirements done | DLQ trigger, signature grouping, auto-replay vs quarantine |
| `07-n8n-agents/22-consumer-lag-predictor` | ✅ Requirements ✅ Design ✅ Tasks | Completion forecast vs cutoff, HITL scale approval |
| `07-n8n-agents/23-market-data-staleness-agent` | ✅ Requirements ✅ Design ✅ Tasks | Feed freshness, crossed-quote detection, blockRiskCalc gate |
| `07-n8n-agents/24-schema-contract-drift-agent` | ✅ Requirements ✅ Design ✅ Tasks | Schema compatibility, consumer impact, breaking-change flag |
| `07-n8n-agents/25-cutoff-calendar-agent` | ✅ Requirements ✅ Design ✅ Tasks | Post-cutoff event detection, holdForNextDay gate |
| `07-n8n-agents/26-contagion-analysis-agent` | ✅ Requirements ✅ Design ✅ Tasks | Neo4j blast radius — trades/books/regions affected |
| `07-n8n-agents/27-service-genome-agent` | ✅ Requirements ✅ Design ✅ Tasks | Service knowledge profile, fragility prediction |
| `07-n8n-agents/28-adaptive-routing-agent` | ✅ Requirements ✅ Design ✅ Tasks | Runtime routing policy proposal, rules validation, HITL apply |
| `07-n8n-agents/29-capacity-backlog-agent` | ✅ Requirements ✅ Design ✅ Tasks | Backlog vs deadline, replica scaling proposal |
| `07-n8n-agents/30-retry-storm-agent` | ✅ Requirements ✅ Design ✅ Tasks ✅ Implemented | Retry amplification, breaker cascade, HITL backpressure |
| `07-n8n-agents/31-finops-cost-agent` | ✅ Requirements ✅ Design ✅ Tasks ✅ Implemented | Cost anomaly, deploy-to-cost correlation, rightsizing |
| `07-n8n-agents/32-trade-amendment-ripple-agent` | ✅ Requirements ✅ Design ✅ Tasks ✅ Implemented | Downstream effect tracking for amendments/cancellations |
| `07-n8n-agents/33-duplicate-effect-guard` | ✅ Requirements ✅ Design ✅ Tasks ✅ Implemented | Double-booking/settlement detection, dry-run reversal |
| `07-n8n-agents/34-transaction-recovery-agent` | ✅ Requirements ✅ Design ✅ Tasks ✅ Implemented | Investigation → Planning → Safety → Execution → Audit |

---

### 08 — AWS Deploy

| Spec | Status | Scope |
|---|---|---|
| `08-aws-deploy/01-eks-cluster` | ✅ Requirements ✅ Design ✅ Tasks | EKS cluster config, node groups, namespaces, IAM roles, Helm chart structure for all Middleware services, ingress controller, HPA config |
| `08-aws-deploy/02-rds-postgres` | ✅ Requirements ✅ Design ✅ Tasks | RDS PostgreSQL 16.x, parameter groups, security groups, Multi-AZ, RDS Proxy connection pooling, Flyway migration strategy, backup/retention |
| `08-aws-deploy/03-msk-kafka` | ✅ Requirements ✅ Design ✅ Tasks | MSK Kafka cluster (3 brokers), broker config, topic creation automation, Glue Schema Registry, IAM auth |
| `08-aws-deploy/04-elasticache-redis` | ✅ Requirements ✅ Design ✅ Tasks | ElastiCache Redis 7.x cluster mode, allkeys-lru eviction, TLS + AUTH, Spring Boot connection config |
| `08-aws-deploy/05-documentdb-mongodb` | ✅ Requirements ✅ Design ✅ Tasks | DocumentDB cluster (MongoDB 7.0 compat), connection string migration, index provisioning, TLS |
| `08-aws-deploy/06-neptune-neo4j` | ✅ Requirements ✅ Design ✅ Tasks | Neptune graph DB (openCypher), Neo4j EKS fallback, data migration strategy, ADR |
| `08-aws-deploy/07-opensearch-elk` | ✅ Requirements ✅ Design ✅ Tasks | OpenSearch domain (ELK replacement), Fluent Bit pipeline, OpenSearch Dashboards, ISM lifecycle, saved queries migration |

---

### 09 — Azure Deploy

| Spec | Status | Scope |
|---|---|---|
| `09-azure-deploy/01-aks-cluster` | ✅ Requirements ✅ Design ✅ Tasks | AKS cluster config, node pools, namespaces, managed identity, Helm chart structure, AGIC ingress |
| `09-azure-deploy/02-azure-postgres` | ✅ Requirements ✅ Design ✅ Tasks | Azure Database for PostgreSQL Flexible Server, built-in PgBouncer connection pooling, VNet integration, Flyway migration |
| `09-azure-deploy/03-azure-event-hub` | ✅ Requirements ✅ Design ✅ Tasks | Azure Event Hub as Kafka-compatible broker, Premium namespace, consumer groups, OAUTHBEARER auth, schema registry |
| `09-azure-deploy/04-azure-cache-redis` | ✅ Requirements ✅ Design ✅ Tasks | Azure Cache for Redis 7.x Premium clustered, TLS, Lettuce client, Entra ID auth |
| `09-azure-deploy/05-cosmos-mongodb` | ✅ Requirements ✅ Design ✅ Tasks | Cosmos DB MongoDB API 7.0, partition key strategy, autoscale RU/s, connection string migration |
| `09-azure-deploy/06-azure-monitor-elk` | ✅ Requirements ✅ Design ✅ Tasks | Azure Monitor + Log Analytics as ELK replacement, OTel → Azure Monitor pipeline, Application Insights, KQL queries, 8 workbooks |

---

## Model Portfolio (Phase 07 agents)

| Role | Implementation | When |
|---|---|---|
| Perception / extraction | Claude Haiku 4.5 | Log normalization, event parsing, rule trace → readable |
| Mid-tier reasoning | Claude Sonnet 5 | Regional agents, intent routing, summarization |
| Deep reasoning / planning | Claude Opus 4.8 | Causal analysis, recovery planning, multi-factor explanation |
| Statistical detection | Python sidecar | Anomaly detection, forecasting — never an LLM |
| Embedding / recall | Embedding model | Similar-incident retrieval, rule corpus search |
| Policy / materiality | Drools (deterministic) | Permitted actions — never an LLM |
| Official arithmetic | Spring Boot services | Risk, exposure, canonical state — never an LLM |

---

## Progress

Progress is tracked at the **requirements** stage (Kiro `requirements-first` workflow). "Done" = `requirements.md` complete; `design.md` and `tasks.md` follow in a later pass.

| Phase | Total Specs | Requirements Done | Design Done | Tasks Done | Remaining (req) |
|---|---|---|---|---|---|
| 01-initial-setup | 2 | 2 ✅ | 1 ✅ (+1 n/a) | 1 ✅ (+1 n/a) | 0 |
| architecture-golden-path | 1 | 1 ✅ | n/a | n/a | 0 |
| 02-microservices | 7 | 7 ✅ | 7 ✅ | 7 ✅ | 0 |
| 03-events | 4 | 4 ✅ | 4 ✅ | 4 ✅ | 0 |
| 04-portals | 3 | 3 ✅ | 3 ✅ | 3 ✅ | 0 |
| 05-observability | 4 | 4 ✅ | 4 ✅ | 4 ✅ | 0 |
| 06-local-deploy | 3 | 3 ✅ | 3 ✅ | 3 ✅ | 0 |
| 07-n8n-agents | 34 | 34 ✅ | 34 ✅ | 34 ✅ | 0 |
| 08-aws-deploy | 7 | 7 ✅ | 7 ✅ | 7 ✅ | 0 |
| 09-azure-deploy | 6 | 6 ✅ | 6 ✅ | 6 ✅ | 0 |
| **Total** | **71** | **71** | **65** | **65** | **0** |

**Requirements baseline phases 01–06: COMPLETE ✅** (24 of 24 specs done, 2026-07-25)
**Phase 02 microservices: req→design→tasks COMPLETE ✅** (all 7 bounded contexts fully specced, 2026-07-25)
**Phases 01–06: FULL req→design→tasks BLUEPRINT COMPLETE ✅** (2026-07-25) — all 22 buildable specs have requirements+design+tasks; the 2 registry specs (`01-technology-stack`, `architecture-golden-path/01-service-nfrs`) are requirements-only by design. Ready for implementation, starting at the dependency root (`shared-domain-contracts`), after reconciling OPEN-1 (TradeEventType enum gap). Phases 07 (n8n agents), 08/09 (cloud deploy) are the next spec frontier when the local platform is built.

Next spec work (staying in the three-doc flow through Phase 06-local-deploy, no code yet):
`04-portals` (3), `05-observability` (4), `06-local-deploy` (3) each need design.md + tasks.md; `01-initial-setup/02-repo-skeleton` needs design.md + tasks.md. (`01-technology-stack` and `architecture-golden-path/01-service-nfrs` are registry/reference specs — realized inside each service's design, no separate design/tasks.) ✅ `02-microservices` and `03-events` design+tasks COMPLETE.

## Open cross-spec items (surfaced during design, to reconcile before code)

- **OPEN-1 — TradeEventType enum gap.** The `03-events/02-domain-events-model` design catalogues event contracts required by other specs that are NOT among the 15 `TradeEventType` constants in the `shared-domain-contracts` shared kernel: `RISK_CALCULATION_FAILED`, the EOD status/completion event family, and `REPLAY_REQUESTED`. Resolution: extend the shared-kernel `TradeEventType` enum (backward-compatible addition) and update `01-shared-domain-contracts` requirements/design/tasks accordingly, before implementing the event-driven services. Tracked so it is not silently invented in code.

**Refactor backlog** — ✅ **COMPLETE for all microservices.** Every `02-microservices` spec is now technology-agnostic (Technology Roles only — verified: zero product names/versions), business/domain-only (cross-cutting NFRs inherited from `architecture-golden-path/01-service-nfrs` via `(inherited GP-Rq-N)`), and DDD-framed (bounded contexts; `01-shared-domain-contracts` = shared kernel). Requirement counts: shared-kernel 12, trade-capture 5, trade-lifecycle 7, risk 8, eod 7, calendar 6, state-reconciliation 8.
- ✅ `02-repo-skeleton` design+tasks complete — scaffold implemented, all tasks green.

---

## Notes

- `01-initial-setup` covers repo skeleton AND DevOps/Local infrastructure (docker-compose, scripts, root package.json) — they are the same feature, different requirements within it.
- Phase 02 microservices are built as plain Spring Boot REST + Kafka + JPA. No Spring AI or MCP dependency until `06-local-deploy/01-mcp-server-setup`.
- Phase 06 wires MCP server, Python sidecars, and n8n together locally before any cloud deploy.
- AWS and Azure deploy phases are independent — either can be done first or skipped.
- All spec folders follow pattern: `{phase-number}-{phase-name}/{spec-number}-{spec-name}/`
