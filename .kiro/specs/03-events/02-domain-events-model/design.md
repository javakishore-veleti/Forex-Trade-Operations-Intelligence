# Design Document — Domain Events Model (Schema Catalogue)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). This document realizes `requirements.md` for the Domain Events Model. Unlike requirements (which are technology-agnostic), **design is where Technology Roles resolve to concrete products** — per `01-initial-setup/01-technology-stack` — and where cross-cutting concerns get concrete form. Every design decision below traces to a requirement (see §11).
>
> **Nature of this spec — read first.** This is a **schema-catalogue / contract spec**, *not* a running `Middleware/` service. It ships no application context, no HTTP endpoints, no persistence, no consumer. It defines the **wire contracts** (the event schemas) that producing and consuming services publish and read on the `EVENT_STREAM`. Consequently **most golden-path service NFRs do not apply here** and are marked N/A in §9; the golden-path requirements that *do* bite (correlation-id propagation, idempotency-by-`eventId`, atomic publish, determinism, synthetic data) are honoured by the **envelope contract** this spec defines and are *implemented* by each producing/consuming service, not here. The substance of this document is §3 (the event catalogue) and §4 (schema versioning & compatibility).

## 1. Overview

The Domain Events Model is the **single authoritative catalogue** of every domain event that flows on the platform `EVENT_STREAM`. For each event type it fixes: the common `EventEnvelope`, the event-specific `EventPayload` field set, the `SchemaSubject` under which the schema is registered, and the target topic and partition key. It is a *contract*, not code — but the contract is expressed as **registered, machine-validated schemas** so that producers and consumers are held to it mechanically rather than by convention.

Every event type in this catalogue is keyed to the `TradeEventType` enum owned by the `shared-domain-contracts` shared kernel (`02-microservices/01-shared-domain-contracts`, Req 3.4 — 15 constants). This spec adds only the **field-level payload contract** per type; the shared kernel supplies the corresponding `SERVICE_LANGUAGE` types and the on-wire `TradeEvent` envelope shape (`eventId`, `tradeId`, `correlationId`, `eventType`, `occurredAt`, `sequenceNumber`, `sourceService`, `payload`).

**Role → concrete binding** (resolved from the technology-stack registry — the *only* place these products are otherwise named):

| Technology Role | Concrete product | Use in this catalogue |
|---|---|---|
| `EVENT_STREAM` | Apache Kafka (KRaft) + its Schema Registry | transport of every catalogued event; `SchemaRegistry` stores & enforces every schema |
| `SERIALIZATION` (in-process) | Jackson (JSON, ISO-8601 temporals, JSON-number monetary) | how `SERVICE_LANGUAGE` producers/consumers (de)serialize `TradeEvent`/payloads in the JVM |
| **Wire schema format** | **Avro** (registered), with a **JSON Schema** projection published for documentation & non-JVM consumers | the registered, version-controlled contract per `SchemaSubject`; Avro is the canonical registry artefact, JSON Schema mirrors it in the catalogue |
| `SchemaRegistry` compatibility | **BACKWARD** (default, per `03-events/01-kafka-topic-design` Req 5.2) | governs schema evolution for every `SchemaSubject` |
| `SERVICE_LANGUAGE` types | Java 21 records from `shared-domain-contracts` | the compile-time projection of these schemas (`TradeEvent`, `TradeEventType`, `Money`, `CurrencyPair`, `ContributingFactor`, …) |
| `UNIT_TEST_FRAMEWORK` / `PROPERTY_TEST` | JUnit 5 / jqwik | round-trip and compatibility assertions (§8) |

> **Choice of Avro-as-canonical, JSON-Schema-as-mirror.** The registry stores Avro because Avro's resolution rules give precise, tool-enforced BACKWARD compatibility (reader/writer schema resolution, defaulted new fields). JSON Schema is emitted alongside purely as human-readable catalogue documentation and for any non-JVM consumer; where the two ever disagree, the registered Avro schema is authoritative.

## 2. Envelope design — `EventEnvelope` vs the shared-kernel `TradeEvent` (Req 1)

Two closely related shapes exist; keeping their relationship explicit is a core design decision.

- **`TradeEvent`** (shared kernel, Req 3.1): the **in-process** JVM record every service passes around — `eventId`, `tradeId`, `correlationId`, `eventType`, `occurredAt`, `sequenceNumber`, `sourceService`, `payload` (`Map<String,Object>`).
- **`EventEnvelope`** (this spec, Req 1.1): the **on-wire** record actually serialized to the topic. It is the `TradeEvent` fields **plus two transport-only fields** the JVM record does not carry: `schemaVersion` (integer, the registered `SchemaSubject` version) and `publishedAt` (ISO-8601 broker write time, distinct from business `occurredAt`).

| Envelope field | Type | Source of truth | Notes |
|---|---|---|---|
| `eventId` | UUID (string) | producer-generated | idempotency key (GP-Rq-5); unique platform-wide (Req 1.2) |
| `eventType` | string ∈ `TradeEventType` | producer | drives consumer routing (Req 1.1) |
| `schemaVersion` | integer | `SchemaRegistry` | matches registered subject version (Req 1.5); unhandleable version → `DLQ` |
| `correlationId` | UUID (string) | propagated from upstream (GP-Rq-2) | never freshly minted if upstream context exists (Req 1.3) |
| `tradeId` | string `FX-######` | producer | business key; the partition key for trade/risk topics |
| `sequenceNumber` | long (≥ 0) | producer | monotonic per `tradeId`; enables ordering/gap detection (Req 1 + §6) |
| `sourceService` | string | producer | the `spring.application.name` that published (Req 1) |
| `occurredAt` | ISO-8601 instant | producer | **business** time of the fact (Req 1.4) |
| `publishedAt` | ISO-8601 instant | producer at send | **broker write** time (Req 1.4) |
| `payload` | object | producer | event-type-specific fields per §3 |

**Malformed-event rule (Req 1.6):** any event with a null/blank required envelope field is malformed → routed to `DLQ` (`fxops.dlq.trade-events`) by the consuming service. This spec defines the constraint; the DLQ mechanics live in `03-events/04-dlq-management`.

## 3. Domain-event catalogue

Legend: **Topic** and **PartitionKey** per `03-events/01-kafka-topic-design`; **Subject** = `{TopicName}-value`; every event additionally carries the full §2 envelope. Monetary fields are JSON numbers at fixed scale; date/instant fields are ISO-8601 strings (Req 2.4/2.5). All examples use synthetic `FX-` ids and fictional names.

### 3.1 Trade lifecycle events → `TradeEventType` (topic `fxops.trade.events`, key `tradeId`)

| # | `TradeEventType` | Payload fields | Contract notes |
|---|---|---|---|
| 1 | `TRADE_CAPTURED` | `tradeId`, `currencyPair{base,quote,pairCode}`, `notionalAmount`, `notionalCurrency`, `direction`, `tradeDate`, `valueDate`, `counterpartyId`, `tradingBookId`, `regionCode` | **First event; carries the complete captured trade** so downstream contexts bootstrap their own state (Req 2.3). `currencyPair` mirrors shared-kernel `CurrencyPair`. |
| 2 | `TRADE_VALIDATED` | `tradeId`, `validatedAt` | Only fields known at validation (Req 2.2) — no null-filling. |
| 3 | `TRADE_ENRICHED` | `tradeId`, `enrichedAt`, `marketDataSnapshotId` | `marketDataSnapshotId` links to the market-data snapshot used. |
| 4 | `RISK_CALCULATION_REQUESTED` | `tradeId`, `calculationRequestId`, `currencyPair`, `notionalAmount`, `notionalCurrency`, `regionCode`, `tradingBookId`, `marketDataSnapshotId`, `requestedAt` | Decouples risk from capture (Req 4.1). `calculationRequestId` is the async correlation key. Canonically also carried on `fxops.risk.requests` (see §7). |
| 5 | `RISK_CALCULATION_COMPLETED` | `tradeId`, `calculationId`, `calculationRequestId`, `riskAmount`, `riskCurrency`, `riskLevel`, `contributingFactors[]{factorName,contribution,currency}`, `ruleVersion`, `rulesFired[]`, `calculatedAt` | `calculationRequestId` matches the request (Req 4.4). `contributingFactors` sum to `riskAmount` within rounding tolerance (Req 4.5). Induces the trade-lifecycle `RISK_CALCULATED` state (see §5 note). |
| 6 | `TRADE_BOOKED` | `tradeId`, `bookedAt`, `bookingDate`, `regionCode` | |
| 7 | `TRADE_ALLOCATED` | `tradeId`, `allocatedAt` | |
| 8 | `TRADE_CONFIRMED` | `tradeId`, `confirmedAt`, `counterpartyId` | |
| 9 | `TRADE_SETTLED` | `tradeId`, `settledAt`, `settlementDate`, `nostroAccount` (synthetic) | Terminal happy-path event. |
| 10 | `TRADE_AMENDED` | `tradeId`, `amendedAt`, `amendedBy` (synthetic), `amendedFields[]{fieldName,previousValue,newValue}`, `amendmentReason` | Only changed fields appear in `amendedFields` (Req 3.3). |
| 11 | `TRADE_CANCELLED` | `tradeId`, `cancelledAt`, `cancelledBy` (synthetic), `cancellationReason` | **Terminal** for the trade; no further lifecycle events for that `tradeId` (Req 3.4). |
| 12 | `TRADE_FAILED` | `tradeId`, `failedAt`, `failureReason`, `failedStage` | `failedStage` names the lifecycle stage that failed. |
| 13 | `EVENT_REPLAYED` | `tradeId`, `replayedFromEventType`, `replayFromSequence`, `replayedThroughSequence`, `replayBatchId`, `sourceReplayRequestId`, `replayedAt` | Emitted by a recovery workflow to record that a replay executed; `sourceReplayRequestId` links to the originating `REPLAY_REQUESTED` (§3.4). Marks replayed records so consumers can distinguish replays from live traffic. |
| 14 | `PROCESSING_PAUSED` | `tradeId`, `pausedAt`, `pausedBy` (synthetic), `pauseReason`, `pausedAtStage` | Signals that lifecycle advancement for a `tradeId` is intentionally halted. |
| 15 | `PROCESSING_RESUMED` | `tradeId`, `resumedAt`, `resumedBy` (synthetic), `resumeReference`, `resumedAtStage` | Clears a prior `PROCESSING_PAUSED`; `resumeReference` is the approval/ticket reference (synthetic). |

Each row is a registered schema under subject `fxops.trade.events-value` (one subject, an Avro **union**/record discriminated by `eventType`; per-type payload records nested). This preserves single-topic ordering per `tradeId` while keeping each payload strongly typed.

### 3.2 Risk events (topic `fxops.risk.results` / `fxops.risk.requests`, key `tradeId`)

`RISK_CALCULATION_REQUESTED` (row 4) is the canonical payload on `fxops.risk.requests`; `RISK_CALCULATION_COMPLETED` (row 5) on `fxops.risk.results`. One further risk event is defined in requirements but **is not (yet) a `TradeEventType` constant** — see §3.5:

| EventType | Topic | Payload fields | Notes |
|---|---|---|---|
| `RISK_CALCULATION_FAILED` | `fxops.risk.results` | `tradeId`, `calculationRequestId`, `failureReason`, `failedAt` | Announces risk could not be computed (Req 4.3); `calculationRequestId` links back to the request (Req 4.4). |

### 3.3 EOD status events (topic `fxops.eod.status`, **key `regionCode`**)

These are **region-keyed**, not trade-keyed (Req 5.2), and are **not** `TradeEventType` constants (§3.5). `globalBusinessDate` is the ISO-8601 date being closed, not wall-clock (Req 5.3).

| EventType | Payload fields | Notes |
|---|---|---|
| `REGIONAL_CLOSE_STARTED` | `regionCode`, `globalBusinessDate`, `startedAt` | |
| `REGIONAL_CLOSE_READY` | `regionCode`, `globalBusinessDate`, `readyAt`, `branchCount`, `completedBranchCount` | |
| `REGIONAL_CLOSE_BLOCKED` | `regionCode`, `globalBusinessDate`, `blockedAt`, `blockerCode`, `blockerDescription` | |
| `REGIONAL_CLOSE_CLOSED` | `regionCode`, `globalBusinessDate`, `closedAt` | |
| `GLOBAL_CONSOLIDATION_COMPLETED` | `globalBusinessDate`, `consolidatedAt`, `regionSummary[]{regionCode,status}` | Published **exactly once** per `globalBusinessDate`; re-publish is a duplicate, idempotent by `globalBusinessDate` (Req 5.4). |

### 3.4 Replay / reprocessing events (topic `fxops.trade.events`, key `tradeId`)

| EventType | Payload fields | Notes |
|---|---|---|
| `REPLAY_REQUESTED` | `tradeId`, `replayFromEventType` (a `TradeEventType`), `requestedBy` (synthetic), `requestedAt`, `approvalReference` | `approvalReference` is **non-null** — an event without a valid one is unauthorized → `DLQ` (Req 6.2). Producible **only** by `state-reconciliation-service` or a gated recovery workflow (Req 6.3). Consumers treat it as a **medium-risk action** and verify `approvalReference` before executing (Req 6.4). `REPLAY_REQUESTED` triggers work whose *completion* is recorded by `EVENT_REPLAYED` (row 13). |

### 3.5 Relationship of the catalogue to the current `TradeEventType` enum

The 15 `TradeEventType` constants (§3.1) are the canonical, enum-typed events on `fxops.trade.events`. Three event **families** in requirements are defined here but are **not** members of the current 15-constant enum:

- `RISK_CALCULATION_FAILED` (§3.2),
- the EOD status family (§3.3),
- `REPLAY_REQUESTED` (§3.4) — distinct from the enum's `EVENT_REPLAYED`, which records completion.

**Design decision / open contract note (see §10 ADR-4):** these carry the full §2 envelope but their `eventType` is a **string not presently in `TradeEventType`**, which is in tension with Req 1.1 ("string matching the `TradeEventType` enum"). This spec catalogues them as first-class contracts and flags that the shared kernel's `TradeEventType` must be **extended** (a backward-compatible enum addition) to admit them, *or* that a sibling enum (`OperationalEventType`) be introduced. The resolution is an ADR owned jointly by this spec and `shared-domain-contracts`; until then, EOD/risk-failed/replay-requested consumers match on the string form. **The trade-lifecycle catalogue (§3.1) is complete and closed against the current enum.**

## 4. Schema versioning & compatibility policy (Req 1.5; topic-design Req 5)

- **One `SchemaSubject` per topic-value** (`{TopicName}-value`), registered before the first producer deploys (topic-design Req 5.1).
- **Default `CompatibilityMode` = BACKWARD** (topic-design Req 5.2): a new schema version must be readable by consumers built against the previous version. Concretely, **backward-compatible evolution** permits: adding an **optional** field (with an Avro default), widening a numeric type per Avro promotion rules, and adding a new event variant to the discriminated union. It **forbids**: removing/renaming a field, tightening a type, or making an optional field required — those are **breaking**.
- **Breaking change procedure (topic-design Req 5.3):** an ADR is authored first; the subject's compatibility is explicitly widened to `FULL` or `NONE` **only for the migration window**, then restored to `BACKWARD`.
- **`schemaVersion` handshake (Req 1.5):** the envelope's `schemaVersion` equals the registered subject version. A consumer that cannot handle a received `schemaVersion` routes the message to the `DLQ` rather than guessing.
- **Producer-side validation (topic-design Req 5.4):** every producer validates the outbound payload against the registered schema *before* publishing; a payload that fails validation is **not** published and is logged with the `CorrelationId`.
- **Registry as readiness dependency (topic-design Req 5.5):** the `SchemaRegistry` is a readiness dependency (GP-Rq-4) for every producer service — implemented in each service, not here.

## 5. Relationship to `shared-domain-contracts` types (Req — cross-spec)

The schemas here are the **wire projection** of shared-kernel types; the kernel is the compile-time projection of these schemas. Correspondence:

| Catalogue element | Shared-kernel type | Relationship |
|---|---|---|
| `EventEnvelope` (minus `schemaVersion`,`publishedAt`) | `TradeEvent` record | envelope = `TradeEvent` fields + 2 transport fields (§2) |
| `eventType` values | `TradeEventType` enum (Req 3.4) | 1:1 for §3.1; §3.5 flags the extension gap |
| `RISK_CALCULATION_COMPLETED.contributingFactors[]` | `ContributingFactor` record | `{factorName, contributionAmount, currency}` |
| monetary payload fields (`notionalAmount`, `riskAmount`) | `Money` / `BigDecimal` scale-2 | JSON number, fixed scale, per `SERIALIZATION` |
| `currencyPair{base,quote,pairCode}` | `CurrencyPair` record | structural mirror |
| `regionCode` values | `RegionCode` enum | `APAC`/`EMEA`/`AMERICAS`/`GLOBAL` |
| `direction` values | `TradeDirection` enum | `BUY`/`SELL` |

**Round-trip guarantee (design intent):** a `TradeEvent` (kernel) serialized via the kernel's `DomainObjectMapper`, published, and read back must deserialize to an equal `TradeEvent`; the payload map round-trips for the supported value types (kernel design §3.4). §8 makes this a test.

## 6. Correlation & sequence semantics (Req 1)

- **`correlationId` (GP-Rq-2):** one UUID per logical request, propagated across every event, log line, and API hop; a producer adopts the upstream `correlationId` and only mints a new one when no upstream context exists (Req 1.3). A single `TRADE_CAPTURED` request thus shares one `correlationId` across its entire downstream lifecycle chain.
- **`eventId` (GP-Rq-5):** globally unique per event instance; the consumer idempotency key. Redelivery of the same `eventId` is a no-op.
- **`sequenceNumber`:** monotonic **per `tradeId`**, assigned by the producing context. Because `tradeId` is the partition key (topic-design Req 2.1), all events for one trade land on one partition and are broker-ordered; `sequenceNumber` lets the `event-sequence-processor` (`03-events/03`) detect gaps, duplicates, and out-of-order arrivals independently of partition offset. `occurredAt` (business time) and `publishedAt` (broker time) are both present so consumers never conflate the two (Req 1.4).

## 7. Topic / subject / partition-key mapping (topic-design Req 1–2)

| EventType family (§3) | Topic | PartitionKey | Subject |
|---|---|---|---|
| Trade lifecycle §3.1 (rows 1–3, 6–15) | `fxops.trade.events` | `tradeId` | `fxops.trade.events-value` |
| `RISK_CALCULATION_REQUESTED` | `fxops.risk.requests` | `tradeId` | `fxops.risk.requests-value` |
| `RISK_CALCULATION_COMPLETED`, `RISK_CALCULATION_FAILED` | `fxops.risk.results` | `tradeId` | `fxops.risk.results-value` |
| EOD status §3.3 | `fxops.eod.status` | `regionCode` | `fxops.eod.status-value` |
| `REPLAY_REQUESTED`, `EVENT_REPLAYED` | `fxops.trade.events` | `tradeId` | `fxops.trade.events-value` |

## 8. Validation & round-trip testing strategy (Req 7; GP-Rq-12)

This spec has no service, so testing = **contract tests** over the schemas and their kernel projection:

- **Schema well-formedness:** every authored Avro schema parses and registers cleanly against a Testcontainers/registry instance (or the registry's static validation).
- **Compatibility tests (§4):** for each subject, assert the *current* schema is BACKWARD-compatible with the *previous* registered version; assert a representative breaking change (drop a field) is **rejected** by the registry. (`INTEGRATION_TEST_HARNESS` against a registry, or the registry's `test-compatibility` API.)
- **Round-trip tests against `shared-domain-contracts`:** for every §3.1 event, build a synthetic `TradeEvent` from kernel types, serialize with `DomainObjectMapper`, validate against the registered schema, deserialize, and assert equality of envelope + payload (Req — kernel §7; ties this spec to the kernel's round-trip guarantee).
- **Field-presence / omission tests:** assert stage-unknown fields are **omitted, not null** (Req 2.2); assert a null required envelope field is flagged malformed (Req 1.6).
- **Property tests (`PROPERTY_TEST`, jqwik):** generate random synthetic `tradeId`/amount/instant tuples and assert round-trip equality and monetary fixed-scale (Req 2.4).
- **Sum invariant:** for `RISK_CALCULATION_COMPLETED`, assert `contributingFactors` sum to `riskAmount` within tolerance (Req 4.5).
- All fixtures use synthetic `FX-` ids and fictional names (Req 7.4 / GP-Rq-14).

## 9. Golden-path NFRs — applicability (mostly N/A for a catalogue)

| Golden-path | Applies here? | Rationale |
|---|---|---|
| GP-Rq-1 API conventions | **N/A** | no HTTP surface — this is a schema catalogue |
| GP-Rq-2 correlation id | **Contract only** | `correlationId` is a mandated envelope field (§2, §6); *propagation* is implemented by each service |
| GP-Rq-3 error envelope | **N/A** | no HTTP surface |
| GP-Rq-4 readiness | **N/A** here / applies to producers | the `SchemaRegistry` readiness rule binds producer services, not this catalogue |
| GP-Rq-5 idempotency | **Contract only** | `eventId` is the mandated idempotency key (§2, §6); dedup implemented by consumers |
| GP-Rq-6 optimistic locking | **N/A** | no `RELATIONAL_STORE` writes |
| GP-Rq-7 atomic publish | **Contract only** | envelope guarantees `eventId`/`correlationId`/`sourceService`/`occurredAt` (GP-Rq-7.4); atomicity implemented by producers |
| GP-Rq-8 observability | **N/A** | no runtime to instrument |
| GP-Rq-9 security placeholder | **N/A** | no application context |
| GP-Rq-10 resilience | **N/A** | no outbound calls |
| GP-Rq-11 config/profiles | **N/A** | no deployable artifact (registration scripts live in DevOps) |
| GP-Rq-12 testing | **Applies** | §8 contract/round-trip/compatibility tests |
| GP-Rq-13 determinism | **Applies (by construction)** | schemas are static declarations; no LLM authors any schema |
| GP-Rq-14 synthetic data | **Applies** | every example/fixture uses `FX-` ids + fictional names |

## 10. Design decisions (ADR-lite)

- **ADR-1 — Avro canonical, JSON Schema mirror.** Avro's reader/writer resolution gives tool-enforced BACKWARD compatibility with defaulted new fields; JSON Schema is emitted only for human docs and non-JVM consumers. Registered Avro is authoritative on conflict.
- **ADR-2 — Envelope = `TradeEvent` + 2 transport fields.** Rather than fork a new envelope type, the wire `EventEnvelope` is the shared-kernel `TradeEvent` plus `schemaVersion` and `publishedAt`, so services reuse the kernel record and add transport metadata only at the serialization boundary. Keeps one domain shape, avoids drift.
- **ADR-3 — One subject per topic, discriminated union of payloads.** `fxops.trade.events-value` is a single subject carrying all §3.1 variants keyed by `eventType`. This preserves per-`tradeId` ordering on one topic while keeping each payload strongly typed, at the cost of a wider union — acceptable for a fixed, small enum.
- **ADR-4 — Enum-extension gap (§3.5) is surfaced, not silently patched.** EOD, risk-failed, and replay-requested events exceed the current 15-constant `TradeEventType`. This spec catalogues them and flags the required backward-compatible kernel change rather than inventing an untracked enum, keeping the shared kernel the single source of truth.
- **ADR-5 — `occurredAt` (business) vs `publishedAt` (broker) always both present.** Prevents consumers from conflating business time with transport time in replay/audit/latency analysis.

## 11. Requirement → design traceability

| Requirement | Satisfied by |
|---|---|
| Req 1 Event Envelope (common metadata) | §2, §6 |
| Req 2 Trade lifecycle events | §3.1 |
| Req 3 Amendment & cancellation events | §3.1 (rows 10–11) |
| Req 4 Risk events | §3.1 (rows 4–5), §3.2 |
| Req 5 EOD status events | §3.3 |
| Req 6 Replay & reprocessing events | §3.4, §3.1 (row 13) |
| Req 7 Schema catalogue & documentation | §3, §7, tasks `schema-catalogue.md` |
| Schema versioning / compatibility | §4 (realizes topic-design Req 5) |
| Relationship to shared kernel | §5, §3.5 |
| Inherited GP-Rq-* (applicability) | §9 |
