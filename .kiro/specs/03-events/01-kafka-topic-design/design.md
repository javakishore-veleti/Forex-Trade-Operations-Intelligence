# Design Document — Kafka Topic Design (Platform Event-Stream Topology)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). This document realizes `requirements.md` for the platform `EVENT_STREAM` topic topology. Unlike requirements (which are technology-agnostic), **design is where Technology Roles resolve to concrete products** — per `01-initial-setup/01-technology-stack` — and where relevant golden-path expectations (`architecture-golden-path/01-service-nfrs`) get concrete bindings. Every design decision below traces to a requirement (see §12).
>
> **Nature of this spec.** This is an **infrastructure/configuration** spec, **not** a running `Middleware/` Spring Boot service. There is **no Maven service module**, no controllers, and no application runtime. Consequently, most golden-path *service* NFRs are **N/A here**: liveness/readiness probes (GP-Rq-4), the HTTP error envelope (GP-Rq-3), API versioning (GP-Rq-1), the security placeholder (GP-Rq-9), and optimistic locking (GP-Rq-6) apply to *services*, not to topic definitions. The cross-cutting concerns this spec **does** own are the `EVENT_STREAM` conventions themselves — naming, keying/ordering, replication, retention, and schema governance — which every producing/consuming service inherits when it binds to a topic. Two golden-path items *do* project onto this spec: GP-Rq-4 in the narrow sense that the `SchemaRegistry` is a producer readiness dependency (§5), and GP-Rq-14 synthetic-data safety (all names are fictional `fxops.*`).

## 1. Overview

This spec defines the **authoritative topic topology** of the platform `EVENT_STREAM`: the canonical list of topics, how they are named, how messages are keyed and ordered, how they are replicated and retained, and how their schemas are governed. It is the **contract surface** between every producer and consumer on the platform; a change to it is a breaking change requiring an ADR.

The deliverable is **configuration-as-code**, not a service: a declarative topic-registry, a schema-registry configuration set, and a local provisioning script — all under `DevOps/Local/EVENT_STREAM/`.

**Role → concrete binding** (resolved from the technology-stack registry — the *only* place these products are otherwise named):

| Technology Role | Concrete product | Use in this spec |
|---|---|---|
| `EVENT_STREAM` (local) | Apache Kafka in **KRaft mode** (no ZooKeeper) | the broker(s) hosting all `fxops.*` topics |
| `EVENT_STREAM` (cloud, Azure) | Azure Event Hub (Kafka-compatible) | same client contract; topic = Event Hub |
| `SchemaRegistry` | Confluent Schema Registry (Kafka-compatible, standalone container) | stores/validates value schemas per subject |
| `SERIALIZATION` | Jackson JSON (ISO-8601 temporals, JSON-number monetary) → registered as **JSON Schema** subjects | payload shape on every topic |
| `STREAM_PROCESSING` | Kafka Streams | owns `fxops.internal.*` changelog/repartition topics |
| `CONTAINER_RUNTIME` | Docker + Docker Compose | brings up broker + registry + runs provisioning |

**Ordering conventions in force platform-wide** (see §2 Glossary usage): topic names are `fxops.<domain>.<type>`; every trade-scoped topic is keyed by `tradeId`; value schemas live under the `{TopicName}-value` subject with **BACKWARD** compatibility.

## 2. Topic catalogue and naming convention (Req 1)

**Naming convention.** Every topic name follows `fxops.<domain>.<type>[.<qualifier>]`:

- `fxops` — fixed platform namespace prefix (all topics, DLQ, and internal).
- `<domain>` — the owning bounded context (`trade`, `risk`, `eod`, `sequence`).
- `<type>` — the payload family (`events`, `results`, `requests`, `status`, `anomalies`).
- lowercase only; **no** uppercase, spaces, or underscores; multi-word segments use `-` (hyphen), e.g. `fxops.dlq.trade-events` (Req 1.4).
- DLQ topics: `fxops.dlq.<origin-topic-short-name>` (Req 1.2). Internal Kafka-Streams topics: `fxops.internal.<processor-name>.<purpose>` (Req 1.3).

**Platform topic catalogue** (the initial authoritative set — Req 1.5):

| TopicName | Class | PartitionKey | Domain events carried |
|---|---|---|---|
| `fxops.trade.events` | domain | `tradeId` | All trade lifecycle events: `TradeCaptured → … → TradeSettled`, `TradeAmended`, `TradeCancelled` |
| `fxops.risk.requests` | domain | `tradeId` | `RiskCalculationRequested` |
| `fxops.risk.results` | domain | `tradeId` | `RiskCalculationCompleted`, `RiskCalculationFailed` |
| `fxops.eod.status` | domain | `regionCode` | `RegionalCloseStarted`, `RegionalCloseReady`, `RegionalCloseClosed`, `GlobalConsolidationCompleted` |
| `fxops.sequence.anomalies` | domain | `tradeId` | `SequenceViolationDetected` (emitted by the event-sequence-processor) |
| `fxops.dlq.trade-events` | DLQ | `tradeId` | Dead-letters from `fxops.trade.events` |
| `fxops.dlq.risk-requests` | DLQ | `tradeId` | Dead-letters from `fxops.risk.requests` |

Kafka-Streams `fxops.internal.*` topics are **not** enumerated here: their count and co-partitioning are managed by `STREAM_PROCESSING` and auto-created against the source-topic partitioning (Req 2.5, deferred to the event-sequence-processor spec).

## 3. Partition and replication strategy (Req 2, Req 3)

**Partitioning / ordering.** Kafka guarantees order **within a partition**, so the `PartitionKey` is chosen to co-locate every event that must be ordered:

- Trade-scoped topics (`fxops.trade.events`, `fxops.risk.requests`, `fxops.risk.results`, `fxops.sequence.anomalies`) are keyed by **`tradeId`** → all events for one trade land on one partition and are strictly ordered (Req 2.1).
- `fxops.eod.status` is keyed by **`regionCode`** → all EOD transitions for a region are ordered (Req 2.2).
- Each DLQ topic carries the **same key** as its origin (Req 2.4), so a trade's dead-letters stay ordered and co-located with replay tooling.

**Partition count.** Domain event topics start at **6 partitions** — enough for consumer-group parallelism across the six steady-state consuming services; the count is recorded in the registry file and **MUST NOT be reduced without an ADR** (Kafka cannot shrink partitions without breaking key→partition mapping) (Req 2.3).

**Replication & durability.**

| Setting | Multi-broker (≥3) | Single-broker local |
|---|---|---|
| `replication.factor` | **3** (Req 3.1) | **1**, marked `# local-only` (Req 3.2) |
| `min.insync.replicas` | **2** (= RF − 1) (Req 3.4) | 1 |
| producer `acks` (convention) | `all` | `all` |

`min.insync.replicas = RF − 1` lets one broker fail without rejecting producer writes while still requiring a durable quorum. DLQ topics inherit their origin's RF and ISR (Req 3.3).

## 4. Retention and cleanup policy (Req 4)

Each topic sets `cleanup.policy` (`delete` = time/size aged-out; `compact` = keep latest per key) and a retention window:

| Topic | `cleanup.policy` | Retention | Rationale |
|---|---|---|---|
| `fxops.trade.events` | `delete` | **30 days** | lifecycle replay, investigation, reconciliation (Req 4.1) |
| `fxops.risk.requests` | `delete` | **7 days** | same-week risk reprocessing (Req 4.2) |
| `fxops.risk.results` | `delete` | **7 days** | same-week risk reprocessing (Req 4.2) |
| `fxops.eod.status` | `delete` | **90 days** | EOD audit queries (Req 4.3) |
| `fxops.sequence.anomalies` | `delete` | **30 days** | anomaly investigation (Req 4.4) |
| `fxops.dlq.trade-events` | `delete` | **14 days** | triage window; **never compacted** (Req 4.5) |
| `fxops.dlq.risk-requests` | `delete` | **14 days** | triage window; **never compacted** (Req 4.5) |
| `fxops.internal.*` (Streams) | `compact` | n/a (compacted) | state changelogs — keep latest per key (Req 4.6) |

Domain and DLQ topics use `delete` (event log semantics: every event is a distinct fact, not a keyed state to collapse). Only Kafka-Streams changelog/internal topics use `compact`. Retention is expressed in the registry as `retention.ms` alongside a human-readable comment.

## 5. Schema-registry setup, subjects, and compatibility (Req 5)

- **Product & topology.** The `SchemaRegistry` runs as a standalone container alongside the KRaft broker(s) in `DevOps/Local/EVENT_STREAM/`. Payloads are `SERIALIZATION`-shaped JSON registered as **JSON Schema** subjects.
- **Subject naming.** Every event type's value schema is registered under the subject **`{TopicName}-value`** (e.g. `fxops.trade.events-value`) — the standard `TopicNameStrategy`. A schema MUST be registered *before* the first producer for that topic is deployed (Req 5.1).
- **Compatibility mode.** The default for every subject is **BACKWARD** (Req 5.2): a new version can be read by consumers built against the previous version (additive/optional fields, safe removals). Registry config sets a global default of `BACKWARD`; per-subject overrides are the ADR-gated exception.
- **Breaking changes.** A breaking change requires an ADR *before* registration; only then may the subject's mode be widened to `FULL` or `NONE`, **for the duration of the migration only**, then restored (Req 5.3).
- **Producer-side validation.** Every producer validates outbound payloads against the registered schema before publishing; a payload that fails validation is **not** published and is logged with the `CorrelationId` (Req 5.4). *(This is a producer-service obligation surfaced here as a convention; enforcement lives in each service.)*
- **Readiness dependency.** The `SchemaRegistry` is a **producer readiness dependency** (GP-Rq-4 projection): a producer service is not `ready` while the registry is unreachable (Req 5.5).

## 6. Consumer-group naming convention

Consumer groups are named **`fxops.<consuming-service>.<topic-short-name>`** so ownership and subscription are inferable from the group id (parallel to the topic convention). Examples (illustrative, owned by the respective service specs, not created by this infra spec):

| Group id | Consuming service | Source topic |
|---|---|---|
| `fxops.trade-lifecycle.trade-events` | trade-lifecycle-service | `fxops.trade.events` |
| `fxops.risk-engine.risk-requests` | risk-engine-service | `fxops.risk.requests` |
| `fxops.sequence-processor.trade-events` | event-sequence-processor | `fxops.trade.events` |

One group per (service, topic) subscription keeps rebalancing and offset ownership isolated; partition count (§3) bounds the useful parallelism per group.

## 7. DLQ placement (cross-reference)

This spec **declares** the DLQ topics (`fxops.dlq.trade-events`, `fxops.dlq.risk-requests`) and fixes their naming (Req 1.2), keying (Req 2.4), replication (Req 3.3), and 14-day non-compacted retention (Req 4.5). It does **not** define retry budgets, dead-letter envelope shape, or triage/replay workflow — those are owned by **`03-events/04-dlq-management`**. The boundary: *this* spec guarantees the topic exists with the right physical config; the dlq-management spec governs *what goes into it and how it is drained*.

## 8. Topic configuration as code (Req 6)

- **Single source of truth.** A declarative topic-registry under `DevOps/Local/EVENT_STREAM/topics/` holds one entry per topic in §2 — partition count, replication factor, `min.insync.replicas`, `cleanup.policy`, `retention.ms`, and a documented `PartitionKey` (Req 6.1, 6.2). No topic is created by ad-hoc CLI outside the registry.
- **Provisioning on startup.** The `DevOps/Local/EVENT_STREAM/` compose setup includes an init step that reads the registry and creates every topic on first startup, so a clean local environment is fully provisioned with no manual steps (Req 6.3).
- **PR discipline.** When a feature spec needs a new topic, the registry file is updated in the *same* PR (Req 6.4).
- **Synthetic-safe.** All names, retention values, and counts are `Synthetic_Identifier`-safe / fictional `fxops.*` only — no real/proprietary topic names (Req 6.5, GP-Rq-14).

## 9. Testing / validation strategy (topic-config assertions)

Because there is no service runtime, validation is **configuration assertion**, run against a provisioned local broker (`CONTAINER_RUNTIME`):

- **Registry lint** — static assertions on the registry file: every name matches `^fxops\.(dlq\.|internal\.)?[a-z0-9-]+(\.[a-z0-9-]+)*$`; no uppercase/underscore/space (Req 1.4); every domain topic has partitions ≥ 6 (Req 2.3); every DLQ retention ≥ 14 days and `cleanup.policy != compact` (Req 4.5).
- **Provisioning assertion** — after the init step runs against the KRaft broker, describe each topic and assert the *live* config matches the registry (partition count, RF, `min.insync.replicas`, `cleanup.policy`, `retention.ms`) — catching drift between declared and created state.
- **Schema-registry assertion** — assert every domain topic has a `{TopicName}-value` subject registered and its compatibility level resolves to `BACKWARD` (Req 5.1, 5.2).
- **DLQ pairing assertion** — every DLQ topic's RF/ISR/key equals its origin's (Req 3.3, 2.4).
- **Synthetic-data check** — no name outside the `fxops.*` namespace; no real identifiers (Req 6.5, GP-Rq-14).

## 10. Design decisions (ADR-lite)

- **KRaft mode, not ZooKeeper.** Local `EVENT_STREAM` runs Kafka in KRaft mode — fewer moving parts, one fewer container, and the forward-looking default; matches the pinned `3.x` binding.
- **`delete` for event logs, `compact` only for Streams state.** Domain/DLQ topics are append-only fact logs where every event matters for replay/audit; compaction would silently drop superseded-by-key events and break replay. Compaction is reserved for keyed state changelogs (`fxops.internal.*`).
- **`TopicNameStrategy` (`{TopicName}-value`) with BACKWARD default.** One subject per topic keeps the producer/consumer contract one-to-one with the topic; BACKWARD is the safest default for a consumer-heavy fan-out platform (consumers upgrade lazily). Widening is ADR-gated and temporary.
- **`tradeId` as the universal trade-scoped key.** Ordering per trade is the dominant correctness constraint across lifecycle, risk, and anomaly detection; a single key choice keeps co-partitioning trivial and DLQ replay ordered.
- **`min.insync.replicas = RF − 1`.** Balances durability (a real quorum) against availability (survive one broker loss) without over-constraining writes.
- **Config-as-code registry as single source of truth.** Reproducible, reviewable, drift-detectable; forbids ad-hoc topic creation that would diverge from the contract.

## 11. Component / artifact structure

```
DevOps/Local/EVENT_STREAM/
  topics/
    topic-registry.yml            # §2/§8 — one entry per fxops.* topic (SSOT)
    topic-registry.schema.json    # shape the registry file must satisfy (lint)
  schema-registry/
    subjects/                     # one {TopicName}-value JSON Schema per domain topic
    compatibility.config          # global default = BACKWARD
  docker-compose.event-stream.yml # KRaft broker(s) + schema-registry + init step
  scripts/
    provision-topics.sh           # reads topic-registry.yml → creates all topics
    validate-topics.sh            # §9 live-vs-registry + subject assertions
```

No `Middleware/` module, no `pom.xml`, no application code (see §Nature note).

## 12. Requirement → design traceability

| Requirement | Satisfied by |
|---|---|
| Req 1 Naming convention | §2 |
| Req 2 Partition strategy | §3 |
| Req 3 Replication factor / ISR | §3 |
| Req 4 Retention / cleanup policy | §4 |
| Req 5 Schema registry integration | §5 |
| Req 6 Topic config as code | §8, §11 |
| DLQ placement | §7 (defers to `04-dlq-management`) |
| Consumer-group naming | §6 |
| GP-Rq-4 (registry readiness projection) | §5 |
| GP-Rq-14 synthetic data | §8, §9 |
| Validation strategy | §9 |
