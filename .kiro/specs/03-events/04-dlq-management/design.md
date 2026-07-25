# Design Document — DLQ Management (Cross-Cutting Strategy)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). This document realizes `requirements.md` for the platform's Dead-Letter Queue strategy. Unlike requirements (which are technology-agnostic), **design is where Technology Roles resolve to concrete products** — per `01-initial-setup/01-technology-stack` — and where the relevant golden-path NFRs (`architecture-golden-path/01-service-nfrs`) get concrete implementations. Every design decision below traces to a requirement (see §12).
>
> **Nature of this spec.** This is a **cross-cutting strategy/configuration** spec, **not a single running service**. It defines the shared dead-letter handling that **every** `EVENT_STREAM` consumer applies (a shared config module + conventions), plus one small operational reader (`DLQConsumer`) for monitoring. There is no new bounded context, no domain state machine, and no `/api/v1` business surface. Consequently only a subset of the golden path applies concretely here — see §1.3.

## 1. Overview

DLQ management is the single platform standard for what a Kafka consumer does when it cannot process a record: retry with bounded backoff, classify the failure, and — on exhaustion — route the original record verbatim to a per-topic dead-letter topic carrying rich diagnostic headers. A thin `DLQConsumer` reads those dead-letter topics to expose depth/poison metrics, maintain a poison quarantine projection, and feed the later DLQ-triage agent. The gated replay path is defined but human-approved (never automatic).

This spec **owns error-handling policy and DLQ conventions only**. It does not own topic creation semantics (that is `03-events/01-kafka-topic-design`), nor the alert rules themselves (those live in `05-observability/03-otel-metrics-dashboards`), nor the triage agent (a later spec) — it produces the compact envelope the agent consumes.

### 1.1 Role → concrete binding

Resolved from the technology-stack registry — the *only* place these products are otherwise named. This spec binds only the roles it actually touches:

| Technology Role | Concrete product | Use in this strategy |
|---|---|---|
| `SERVICE_LANGUAGE` / `SERVICE_FRAMEWORK` | Java 21 / Spring Boot 3.4.x | shared `fxops-dlq-support` library + `DLQConsumer` runtime |
| `EVENT_STREAM` | Apache Kafka (KRaft, 3.x) | origin topics, `.DLT` dead-letter topics, consumer error handling |
| the consumer error-handling mechanism | Spring Kafka `DefaultErrorHandler` + `DeadLetterPublishingRecommender`/`DeadLetterPublishingRecoverer` with `ExponentialBackOff` | bounded retry then publish-to-DLT |
| `RELATIONAL_STORE` | PostgreSQL 16.x | `dlq_poison_quarantine` projection + `dlq_replay_log` (Req 4.2, Req 6.5) |
| `SERIALIZATION` | Jackson (ISO-8601 temporals) | header value formatting (timestamps), envelope for the triage agent |
| `OBSERVABILITY_METRICS` | Prometheus + Grafana | scrapes `dlq_messages_total`, `dlq_depth`, `dlq_poison_message_count` |
| `OBSERVABILITY_LOGGING` | ELK (8.x) | WARN dead-letter logs + INFO DLQConsumer read logs |
| `INTEGRATION_TEST_HARNESS` | Testcontainers (Kafka + Postgres) | force-a-poison-message end-to-end tests |

> **Naming note.** `requirements.md` uses the operator-facing DLQTopic form `fxops.dlq.{origin-short-name}`. This design adopts the Spring-Kafka-idiomatic **`<origin-topic>.DLT`** suffix as the physical topic name and maps the two one-for-one (§2). Both refer to the same physical topics; the `.DLT` form is what the error handler and provisioning use.

### 1.2 Distribution model

- **`fxops-dlq-support`** — a shared library (added to every consuming module's build) that provides: the retry/DLQ `DefaultErrorHandler` factory, the poison classifier, the `QuarantineHeader` enricher, and the externalized `DLQProperties`. Consumers wire it in; they do **not** re-implement dead-lettering (Req 2.1, single standard).
- **`DLQConsumer`** — one small operational module that reads every `.DLT`, emits metrics, maintains the poison quarantine projection, and logs. It is the only *running* artifact this spec introduces.
- **`gated-replay`** — a capability (a gated tool, per the platform action-gate pattern) that re-publishes a non-poison dead-letter record to its origin topic under `ReplayApproval`.

### 1.3 Golden-path NFR applicability

| Golden-path | Applies here? | How / why |
|---|---|---|
| GP-Rq-5 **Idempotency** | **APPLIES** (core) | Retries are at-least-once by nature; the dedup-by-`eventId` guarantee of consuming services means a retried/redelivered record is safe. Replay (Req 6.5) is made idempotent by keying on `dlq.origin.offset` so the same dead-letter record re-publishes exactly once. |
| GP-Rq-8 **Observability** (of retries) | **APPLIES** (core) | Retry attempts, dead-letter events, and DLQ depth are all instrumented (§6): `dlq_messages_total`, `dlq_depth`, `dlq_poison_message_count`, plus WARN/INFO logs carrying `CorrelationId`. |
| GP-Rq-2 **Correlation ID** | **APPLIES** (narrowed) | The `CorrelationId` is copied from the message envelope into `dlq.correlation.id` and into every DLQ log line — but there is no HTTP `CorrelationIdFilter` here (no request surface). |
| GP-Rq-11 **Configuration/profiles** | **APPLIES** | Retry budget/backoff and thresholds are externalized `DLQProperties` (Req 2.5). |
| GP-Rq-7 **Atomicity / ack-after-commit** | **APPLIES (inherited, not re-specified)** | The *consuming* service already acks-after-commit per its own spec; DLQ publish participates in that unit of work (§3). This spec does not restate it. |
| GP-Rq-13 **Determinism / LLM boundary** | **APPLIES (boundary)** | Classification and routing are deterministic code. The triage *agent* (§7) consumes the envelope but never authors a resolution automatically — poison resolution and replay are human-gated (Req 4.3, Req 6.3). |
| GP-Rq-1 **API conventions & versioning** | **N/A** | No business HTTP surface. `DLQConsumer` exposes only actuator/metrics; the gated-replay tool is an action-gate tool, not a versioned REST resource. |
| GP-Rq-3 **Error envelope** | **N/A** | No HTTP request/response error path (the "errors" here are consumer failures, handled by dead-lettering, not by returning `ErrorEnvelope`s). |
| GP-Rq-4 **Health/readiness** | **N/A for the library; minimal for `DLQConsumer`** | The library adds no service; `DLQConsumer` inherits a standard readiness probe from the golden path but defines nothing bespoke. |
| GP-Rq-6 **Optimistic locking** | **N/A** | The quarantine projection is append-mostly/insert + status-update by a single reader; no concurrent-writer contention model is introduced. |
| GP-Rq-9 **Security placeholder** | **N/A (deferred)** | No new request surface to permit-all; the replay tool's auth is the action-gate/`ReplayApproval`, defined by the HITL-gate spec. |
| GP-Rq-10 **Downstream resilience** | **Related but distinct** | GP-Rq-10 governs *outbound synchronous* calls; DLQ governs *inbound event* processing. The retry policy here is **consistent with** GP-Rq-10's "bounded, backoff-based, idempotency-safe" principle applied to the consume path. |
| GP-Rq-12 **Testing** | **APPLIES** | §10 (Testcontainers force-poison test). |
| GP-Rq-14 **Synthetic data** | **APPLIES** | All ids `FX-######`, all topics fictional `fxops.*`. |

## 2. DLQ topic naming convention (Req 1)

One dead-letter topic **per source topic**, one level deep (Req 1.4). Physical name = origin topic + `.DLT` suffix; this maps one-for-one to the operator-facing `fxops.dlq.*` alias from `requirements.md`:

| OriginTopic | Physical DLQ topic (`.DLT`) | Operator alias (requirements form) |
|---|---|---|
| `fxops.trade.events` | `fxops.trade.events.DLT` | `fxops.dlq.trade-events` |
| `fxops.risk.requests` | `fxops.risk.requests.DLT` | `fxops.dlq.risk-requests` |

Rules (Req 1):
- Every `OriginTopic` with ≥1 consumer has exactly one `.DLT` topic (Req 1.1/1.2).
- A `.DLT` topic **never** has its own `.DLT` — the chain is one level deep; the error handler is not attached to `.DLT` consumers (Req 1.4).
- `.DLT` retention **≥ 14 days**, **no log compaction** (delete cleanup policy) — inherited from `03-events/01-kafka-topic-design` Req 4 (Req 1.5).
- `.DLT` partition count **matches** its origin topic so `PartitionKey` (tradeId) maps to the same partition index (Req 3.4).
- When a new consumer is introduced, its origin topic's `.DLT` is provisioned **in the same PR** (Req 1.3) — enforced via the shared topic-provisioning descriptor (§ Tasks 3).

## 3. Retry policy per topic, then route-to-DLQ (Req 2; consistent with GP-Rq-10)

Each consumer installs the shared `DefaultErrorHandler` configured from `DLQProperties`. Failure flow per record:

1. Attempt processing.
2. On a **retryable** exception → back off and retry, up to the per-topic budget. Backoff is **exponential, bounded** (start → max), never unbounded (consistent with GP-Rq-10.2).
3. On a **non-retryable / poison** exception (schema-validation failure) → **skip remaining retries**, flag poison, dead-letter immediately (Req 2.4).
4. On **budget exhaustion** → enrich headers (§5) and publish the **verbatim original record** to the origin's `.DLT` (Req 3.1/3.3), then ack the origin offset (the record is now durably on the `.DLT`).

Per-topic budgets (externalized `DLQProperties`, Req 2.5 / GP-Rq-11):

| OriginTopic | Max retries | Initial backoff | Max backoff | Source |
|---|---|---|---|---|
| `fxops.trade.events` | 5 | 1s | 30s | Req 2.2 |
| `fxops.risk.requests` | 3 | 2s | 30s | Req 2.3 |

```yaml
# externalized (application.yml) — tunable without code changes (Req 2.5, GP-Rq-11)
fxops.dlq:
  policies:
    "fxops.trade.events": { maxRetries: 5, initialBackoffMs: 1000, maxBackoffMs: 30000, multiplier: 2.0 }
    "fxops.risk.requests": { maxRetries: 3, initialBackoffMs: 2000, maxBackoffMs: 30000, multiplier: 2.0 }
  poison:
    classify-as-poison: ["org.springframework.kafka.support.serializer.DeserializationException", "jakarta.validation.ValidationException", "com.fxtradeops.contracts.SchemaValidationException"]
```

**Non-blocking backoff (Req 2.6).** Backoff MUST NOT block the whole partition. The library uses the **non-blocking retry** model (retry via internal delay topics / `@RetryableTopic`-style staging) rather than in-thread `Thread.sleep` on the listener container, so that other records in the partition continue while a failing record waits. In-thread blocking backoff is explicitly rejected (see §11).

## 4. Poison vs transient classification + quarantine (Req 2.4, Req 4)

**Classification is deterministic** (GP-Rq-13):

| Failure | Class | Action |
|---|---|---|
| `DeserializationException`, schema/`ValidationException` (payload structurally invalid) | **PoisonMessage** | `dlq.poison.flag=true`; skip retries; dead-letter immediately (Req 2.4) |
| Dependency unreachable, timeout, transient store error | **TransientFailure** | retry to budget; if still failing, `dlq.poison.flag=false`; dead-letter (Req 2.1) |

`PoisonClassifier.isPoison(Throwable)` walks the cause chain against the configured `classify-as-poison` list — a pure, unit-testable function.

**Quarantine strategy (Req 4):**
- A poison record (`dlq.poison.flag=true`) is **never** auto-replayed (Req 4.1).
- `DLQConsumer` projects every poison record into `dlq_poison_quarantine` in the `RELATIONAL_STORE` (Req 4.2), keyed by `(origin_topic, trade_id, origin_offset)`, with `failure_reason`, `arrival_ts`, `resolution_state` (`OPEN` | `DISCARDED` | `REPROCESSED`).
- Queryable by `origin_topic`, `trade_id`, `poison_flag`, arrival date range (Req 4.4).
- Resolution only via a human-approved **discard** (audited) or **manual reprocess** (corrected externally, re-submitted via gated replay) (Req 4.3). No automatic process removes a poison entry (Req 4.5) — resolution flips `resolution_state`, it does not delete.

**`dlq_poison_quarantine` (PostgreSQL):**

| column | type | notes |
|---|---|---|
| `origin_topic` | `varchar` | part of natural key |
| `trade_id` | `varchar` | `FX-######` |
| `origin_partition` | `int` | from header |
| `origin_offset` | `bigint` | part of natural key (Req 4.2) |
| `failure_reason` | `varchar(500)` | truncated exception (Req 3.2) |
| `correlation_id` | `varchar` | |
| `arrival_ts` | `timestamptz` | |
| `resolution_state` | `varchar` | `OPEN`/`DISCARDED`/`REPROCESSED` (default `OPEN`) |
| `resolved_by` | `varchar` | synthetic operator id, null until resolved |
| `resolution_approval` | `varchar` | `ReplayApproval` ref when reprocessed |

Unique constraint on `(origin_topic, origin_offset)` gives replay idempotency a natural anchor (§7, Req 6.5).

## 5. DLQ record headers (Req 3)

On dead-lettering, the `QuarantineHeaderEnricher` attaches the full set **before** publishing to `.DLT`. The original payload body is preserved **verbatim** — not modified, re-serialized, or truncated (Req 3.3):

| Header | Type | Value |
|---|---|---|
| `dlq.origin.topic` | string | the `OriginTopic` |
| `dlq.origin.partition` | int | original partition |
| `dlq.origin.offset` | long | original offset |
| `dlq.failure.reason` | string | exception class + message, **truncated to 500 chars** (Req 3.2) |
| `dlq.failure.count` | int | total attempts incl. retries |
| `dlq.failure.timestamp` | string | ISO-8601 (`SERIALIZATION`) |
| `dlq.poison.flag` | boolean | true iff classified poison |
| `dlq.correlation.id` | string | `CorrelationId` from the message envelope (GP-Rq-2) |

`PartitionKey` of the dead-letter record = `PartitionKey` of the original (tradeId), so all DLQ records for a `tradeId` co-locate on one `.DLT` partition (Req 3.4). On dead-letter, a **WARN** log is emitted with `CorrelationId`, `tradeId`, `dlq.origin.topic`, `dlq.origin.offset`, `dlq.failure.reason` (Req 3.5).

## 6. DLQ consumer monitoring + metrics (Req 5)

`DLQConsumer` subscribes to every `.DLT`, and per record: logs at **INFO** (`CorrelationId`, `tradeId`, `dlq.poison.flag`, `dlq.failure.reason` — Req 5.5), updates the quarantine projection for poison records (§4), and increments metrics.

| Metric | Type | Labels | Meaning |
|---|---|---|---|
| `dlq_messages_total` | counter | `origin_topic`, `poison`, `reason_signature` | every dead-lettered record read, bucketed by failure **signature** (normalized exception class), so triage sees "what is failing and how often" |
| `dlq_depth` | gauge | `topic` | consumer-group lag on each `.DLT` (Req 5.1) — scraped by `OBSERVABILITY_METRICS` |
| `dlq_poison_message_count` | gauge | `origin_topic` | count of **unresolved** (`OPEN`) poison entries per origin topic (Req 5.2) |

`reason_signature` = exception class name only (message stripped) to keep label cardinality bounded while still grouping by failure kind.

**Alerts are defined elsewhere** (Req 5.3/5.4): `05-observability/03-otel-metrics-dashboards` defines the `dlq_depth > threshold for duration` alert and the "new poison quarantined" alert. This spec only guarantees the metrics exist and are scrapeable.

## 7. Feeding the DLQ-triage agent (compact envelope, not the raw stream)

Per the platform boundary (technology-stack Req 5.2/5.5), **high-volume streams never flow through an agent**. `DLQConsumer` therefore does not forward `.DLT` records to the agent. Instead it derives a **compact triage envelope** — a small, bounded summary — that the later DLQ-triage agent consumes:

```json
{
  "originTopic": "fxops.trade.events",
  "tradeId": "FX-000501",
  "originPartition": 3,
  "originOffset": 88214,
  "poison": true,
  "failureSignature": "SchemaValidationException",
  "failureReason": "SchemaValidationException: field 'notional' missing (…truncated 500)",
  "failureCount": 1,
  "correlationId": "FX-corr-7ac3…",
  "firstSeen": "2026-01-15T09:41:22Z",
  "quarantineState": "OPEN",
  "suggestedActions": ["discard", "manual-reprocess"]
}
```

- The envelope carries **headers + signature + quarantine state**, never the raw payload body (that stays on the `.DLT`, retrievable by offset if a human drills in).
- The agent is a **read/triage** consumer: it may propose `discard` or `manual-reprocess`, but any resolution or replay is **human-gated** (Req 4.3, Req 6.3) — the agent never mutates quarantine state or replays autonomously (GP-Rq-13 boundary).
- **Gated replay (Req 6):** the `gated-replay` tool reads a **non-poison** dead-letter record (rejects if `dlq.poison.flag=true`, Req 6.2), requires a valid `ReplayApproval` (Req 6.3), re-publishes to the origin topic with a **new `eventId`**, the **original `correlationId`**, and a `dlq.replay.approval` header (Req 6.4). Idempotency: keyed on `(origin_topic, origin_offset)` via `dlq_replay_log` unique constraint — replaying the same dead-letter record twice yields exactly one re-published message (Req 6.5). Every replay is written to `dlq_replay_log` with operator identity (synthetic), approval ref, origin topic/offset, tradeId, timestamp (Req 6.6).

## 8. Sequence — dead-letter, monitor, triage

```mermaid
sequenceDiagram
  participant K as Kafka (fxops.trade.events)
  participant CS as Consuming Service (fxops-dlq-support)
  participant EH as DefaultErrorHandler
  participant DLT as fxops.trade.events.DLT
  participant DC as DLQConsumer
  participant PG as Postgres (quarantine)
  participant AG as DLQ-triage agent
  K->>CS: record
  CS->>CS: process → exception
  alt poison (schema/deser)
    CS->>EH: non-retryable
    EH->>DLT: publish verbatim + QuarantineHeaders (poison=true)
  else transient
    EH->>EH: retry (exp backoff, non-blocking, bounded)
    EH->>DLT: on exhaustion: publish verbatim + headers (poison=false)
  end
  DC->>DLT: read
  DC->>DC: INFO log; inc dlq_messages_total{signature}
  alt poison
    DC->>PG: upsert quarantine (OPEN)
    DC->>DC: set dlq_poison_message_count
  end
  DC->>AG: compact triage envelope (no raw payload)
  Note over AG: proposes discard / manual-reprocess — human-gated only
```

## 9. Error handling within the DLQ path

- The `.DLT` publish itself must not silently fail: if publishing to `.DLT` fails, the origin offset is **not** acked → Kafka redelivery → retried (safe under consumer dedup, GP-Rq-5). One-level-deep guarantee means no `.DLT`-of-`.DLT`.
- `DLQConsumer` projection write failure → offset not acked on the `.DLT` → re-read; the quarantine upsert is idempotent on `(origin_topic, origin_offset)`, so re-reads do not double-count.
- Poison classification is conservative: an unrecognized exception defaults to **transient** (retry then dead-letter with `poison=false`) — we never auto-mark poison on an unknown error, so nothing is wrongly excluded from replay eligibility.

## 10. Testing strategy (Req + GP-Rq-12)

- **Unit** (`UNIT_TEST_FRAMEWORK`): `PoisonClassifier` (poison exceptions → true, transient → false, unknown → false); `QuarantineHeaderEnricher` (all 8 headers present, `dlq.failure.reason` truncated at 500, correlationId copied); `DLQProperties` binding for both topics.
- **Integration** (`INTEGRATION_TEST_HARNESS` — Testcontainers **Kafka + Postgres**), the headline test: **force a poison message** onto `fxops.trade.events` (a schema-invalid `FX-` record) → assert it lands on `fxops.trade.events.DLT` with `dlq.poison.flag=true` and the full header set, the **payload byte-identical** to the input, and a `dlq_poison_quarantine` row in `OPEN` state. A second test forces a **transient** failure (dependency stub failing N times) → assert exactly `maxRetries` attempts, exponential spacing, then dead-letter with `poison=false`.
- **Replay** integration: replay a non-poison record twice (same `origin_offset`) → exactly one re-published message on the origin topic, new `eventId`, original `correlationId`, `dlq.replay.approval` header present; replay of a poison record → rejected (Req 6.2).
- **Metrics** assertion: `dlq_messages_total{origin_topic,poison,reason_signature}` and `dlq_poison_message_count{origin_topic}` present after the poison test.
- All fixtures use synthetic `FX-` ids and fictional `fxops.*` topics (GP-Rq-14).

## 11. Design decisions (ADR-lite)

- **`.DLT` suffix as physical name, `fxops.dlq.*` as the operator alias** (not two topic families): one physical topic set; `.DLT` is the Spring-Kafka-idiomatic convention the error handler emits, and the alias keeps operator docs readable. Mapping is one-for-one (§2).
- **Shared library, not copy-paste per service** (Req 2.1): a single `fxops-dlq-support` module means the retry budget, classifier, and header contract exist in exactly one place; deviation requires an ADR (per requirements' single-standard rule).
- **Non-blocking retry over in-thread `Thread.sleep`** (Req 2.6): blocking backoff would stall the whole partition (head-of-line blocking) and violate Req 2.6; staged delay-topic retry lets healthy records in the partition proceed. Accepted cost: retry ordering within a key is relaxed — acceptable because dead-lettering is a failure path, not the happy path.
- **Poison classification defaults to transient on unknown** (§9): being wrong-and-retry is recoverable; being wrong-and-quarantine wrongly strands a replayable message behind a human gate. Fail toward replayability.
- **Compact envelope to the agent, raw payload stays on `.DLT`** (§7): honors the platform "no high-volume stream through an agent" boundary and keeps the agent's context bounded; the payload is always retrievable by offset for a human.
- **Quarantine resolution flips state, never deletes** (Req 4.5): the quarantine list is an audit surface; discard/reprocess are auditable state transitions, not row deletions.

## 12. Requirement → design traceability

| Requirement | Satisfied by |
|---|---|
| Req 1 DLQ topic catalogue | §2 |
| Req 2 Per-topic retry policy | §3, §4 (classification), §11 (non-blocking) |
| Req 3 Dead-lettering & quarantine headers | §5 |
| Req 4 Poison quarantine | §4, §7 (resolution), §6 (count) |
| Req 5 Monitoring & alerting | §6 (metrics; alerts referenced to `05-observability`) |
| Req 6 Gated replay | §7, §4 (`dlq_replay_log`) |
| Golden-path applicability | §1.3 |
