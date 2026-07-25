# Requirements Document — Event Sequence Processor

> **Inherits `architecture-golden-path/01-service-nfrs`** (all cross-cutting
> NFRs) and references **Technology Roles** from
> `01-initial-setup/01-technology-stack`. This spec contains **only
> business/domain requirements** plus any service-specific narrowing of a
> golden-path requirement. No product names, versions, or repeated NFRs.

## Introduction

The **Event Sequence Processor** is a stateful `STREAM_PROCESSING` application
that runs continuously alongside the `EVENT_STREAM`. Its single responsibility
is to **detect sequencing anomalies** in the trade event stream: missing events,
duplicate events, and out-of-order events for a given `tradeId`. When a
violation is detected it emits a compact **anomaly envelope** to the
`fxops.sequence.anomalies` topic so that downstream agents and monitoring
systems can react — but this processor never takes corrective action itself.

It does **not** advance trade lifecycle state (that is the Trade Lifecycle
bounded context), it does **not** compute risk or expose REST APIs, and it
**never invokes a model** — all detection logic is deterministic
`STREAM_PROCESSING` over the event sequence (per inherited GP-Rq-13).

Cross-cutting concerns are **inherited from the golden path** and are not
restated here. All identifiers use the synthetic `FX-` prefix; all service
names are fictional.

---

## Bounded Context and Ubiquitous Language

- **Event Sequence Processor**: This `STREAM_PROCESSING` application.
- **SequenceFact**: The per-`tradeId` running state maintained in a
  `STREAM_PROCESSING` state store: `{tradeId, observedEvents[], expectedNextEvents[], missingEvents[], duplicateEventIds[], sequenceViolations[]}`.
- **ObservedEvent**: An event for a `tradeId` that has been seen on the stream.
- **ExpectedNextEvents**: The set of `TradeEventType` values that are valid
  next transitions from the last observed status, derived from the lifecycle
  state machine.
- **MissingEvent**: A `TradeEventType` that the lifecycle state machine requires
  before the observed next event but was never seen.
- **DuplicateEvent**: An event whose `eventId` has already been recorded in the
  `SequenceFact` for that `tradeId`.
- **SequenceViolation**: A detected anomaly: a `MissingEvent`, a
  `DuplicateEvent`, or an out-of-order transition.
- **AnomalyEnvelope**: The compact output message published to
  `fxops.sequence.anomalies` when a `SequenceViolation` is detected; contains
  enough context for an agent to investigate without re-reading the full stream.
- **StateStore**: The `STREAM_PROCESSING` persistent key-value store keyed by
  `tradeId` that holds the `SequenceFact` for each active trade.
- **GracePeriod**: The configurable window after a trade's last observed event
  during which a late-arriving event is still accepted without raising a
  `SequenceViolation`.

**Consumed topics** (from `EVENT_STREAM`): `fxops.trade.events`.
**Produced topics** (to `EVENT_STREAM`): `fxops.sequence.anomalies`.
**Persistence roles used**: `STREAM_PROCESSING` state store (backed by the
`EVENT_STREAM` internal changelog topic `fxops.internal.sequence-processor.facts`).

---

## Requirements

### Requirement 1: Per-Trade Sequence Fact Maintenance

**User Story:** As the event integrity layer, I want the processor to maintain
a running `SequenceFact` per `tradeId` so that I can always know which events
have been seen, which are missing, and which have been duplicated for any given
trade.

#### Acceptance Criteria

1. THE Event Sequence Processor SHALL consume every message from
   `fxops.trade.events` and update the `SequenceFact` in the `StateStore`
   for the corresponding `tradeId`.
2. THE `SequenceFact` SHALL record: the ordered list of `ObservedEvent` types
   and their `eventId`s; the computed `ExpectedNextEvents` based on the last
   observed status; the list of `MissingEvent` types detected so far; the list
   of `DuplicateEvent` `eventId`s; and the list of `SequenceViolation`
   descriptions with their detection instants.
3. THE processor SHALL derive `ExpectedNextEvents` from the same state machine
   transition table as the Trade Lifecycle bounded context
   (`02-microservices/03-trade-lifecycle-service`), so that the two contexts
   agree on what a valid next event is.
4. WHEN a terminal event (`TRADE_SETTLED`, `TRADE_CANCELLED`, `TRADE_FAILED`)
   is observed, THE processor SHALL mark the `SequenceFact` as complete and
   MAY expire it from the `StateStore` after the configured retention window.
5. THE `StateStore` SHALL be backed by the `EVENT_STREAM` internal changelog
   topic so that it survives processor restarts without data loss.

---

### Requirement 2: Missing Event Detection

**User Story:** As the operations team, I want the processor to detect when an
expected intermediate event never arrives, so that stuck trades are surfaced
before they cause downstream EOD failures.

#### Acceptance Criteria

1. WHEN an event of type T2 is observed for a `tradeId` but the `SequenceFact`
   does not record the prerequisite event T1 (as defined by the state machine),
   THE processor SHALL record T1 as a `MissingEvent` in the `SequenceFact` and
   emit an `AnomalyEnvelope` of violation type `MISSING_EVENT`.
2. THE `AnomalyEnvelope` for a `MISSING_EVENT` violation SHALL carry:
   `tradeId`, `violationType = MISSING_EVENT`, `missingEventType`, `observedEventType`, `detectedAt`, `correlationId`, and `sequenceFactSnapshot` (the current `SequenceFact` fields relevant to the violation).
3. THE processor SHALL NOT emit duplicate `AnomalyEnvelope`s for the same
   `MissingEvent` on the same `tradeId`; once recorded in the `SequenceFact`,
   a `MissingEvent` SHALL produce at most one anomaly envelope.
4. IF the missing event arrives within the configured `GracePeriod`, THE
   processor SHALL clear the `MissingEvent` from the `SequenceFact` and emit
   a `MISSING_EVENT_RESOLVED` anomaly envelope.

---

### Requirement 3: Duplicate Event Detection

**User Story:** As the event integrity layer, I want the processor to detect
when the same event (by `eventId`) is seen more than once, so that idempotency
violations are surfaced even when the Trade Lifecycle service's dedup silently
absorbs them.

#### Acceptance Criteria

1. WHEN an event arrives whose `eventId` already exists in the `SequenceFact`
   for that `tradeId`, THE processor SHALL record it as a `DuplicateEvent` and
   emit an `AnomalyEnvelope` of violation type `DUPLICATE_EVENT`.
2. THE `AnomalyEnvelope` for a `DUPLICATE_EVENT` violation SHALL carry:
   `tradeId`, `violationType = DUPLICATE_EVENT`, `duplicateEventId`,
   `eventType`, `firstSeenAt`, `duplicateSeenAt`, `correlationId`.
3. THE processor SHALL continue processing subsequent events for the `tradeId`
   after recording a duplicate; it SHALL NOT halt or skip the stream.
4. THE processor SHALL distinguish between a true duplicate (`eventId` seen
   twice with identical payload) and a conflicting replay (`eventId` seen twice
   with differing payload); the latter SHALL be recorded as violation type
   `CONFLICTING_REPLAY` with both payloads included in the `AnomalyEnvelope`.

---

### Requirement 4: Out-of-Order Event Detection

**User Story:** As the operations team, I want the processor to detect when an
event arrives for a status the trade has already passed, so that late-arriving
events that could cause state corruption are flagged immediately.

#### Acceptance Criteria

1. WHEN an event arrives for a `TradeEventType` that maps to a `TradeStatus`
   the trade has already surpassed (i.e. the transition is to a status earlier
   than the current `SequenceFact` status), THE processor SHALL record it as
   an out-of-order `SequenceViolation` and emit an `AnomalyEnvelope` of
   violation type `OUT_OF_ORDER_EVENT`.
2. THE `AnomalyEnvelope` for an `OUT_OF_ORDER_EVENT` violation SHALL carry:
   `tradeId`, `violationType = OUT_OF_ORDER_EVENT`, `arrivingEventType`,
   `arrivingEventStatus`, `currentFactStatus`, `detectedAt`, `correlationId`.
3. THE processor SHALL record the out-of-order event in the `SequenceFact`'s
   observation list (for audit purposes) but SHALL NOT update the
   `SequenceFact` current status from it.

---

### Requirement 5: Anomaly Envelope Publication

**User Story:** As a downstream agent, I want anomaly envelopes published to a
dedicated topic so that I can subscribe once and receive all sequence violation
signals without reading the full trade event stream.

#### Acceptance Criteria

1. EVERY `AnomalyEnvelope` SHALL be published to the `fxops.sequence.anomalies`
   topic (per `03-events/01-kafka-topic-design`) with `tradeId` as the
   `PartitionKey`.
2. THE `AnomalyEnvelope` SHALL carry the standard `EventEnvelope` metadata
   (per `03-events/02-domain-events-model` Requirement 1) plus the
   violation-specific fields defined in Requirements 2–4.
3. THE processor SHALL publish the `AnomalyEnvelope` within one processing
   interval of detecting the violation; it SHALL NOT buffer anomalies.
4. THE `AnomalyEnvelope` payload SHALL be self-contained: a consumer SHALL be
   able to understand the violation from the envelope alone without querying
   external services.
5. THE processor SHALL NOT publish `AnomalyEnvelope`s for events that are
   within the `GracePeriod` for a `MissingEvent`; it SHALL wait until the
   grace period elapses before emitting.

---

### Requirement 6: Processor Resilience and Exactly-Once Guarantees

**User Story:** As a platform operator, I want the sequence processor to
survive restarts without losing state or producing duplicate anomalies, so
that detection is reliable even during infrastructure disruptions.

#### Acceptance Criteria

1. THE processor SHALL use `STREAM_PROCESSING` exactly-once semantics for
   state updates and anomaly publications, so that a restart does not result
   in duplicate `AnomalyEnvelope`s or missed violations.
2. WHEN the processor restarts, THE `StateStore` SHALL be restored from its
   `EVENT_STREAM` changelog topic so that in-progress `SequenceFact`s are
   recovered without re-reading the full `fxops.trade.events` topic.
3. THE processor SHALL expose a health/liveness probe (inherited GP-Rq-4)
   that reports `DOWN` if the `STREAM_PROCESSING` thread is not running or if
   the `StateStore` is not restored.
4. THE `GracePeriod` value SHALL be externalized as configuration (inherited
   GP-Rq-11) and SHALL default to a documented value; it SHALL NOT be
   hard-coded.
5. ALL `SequenceFact` entries SHALL use `Synthetic_Identifier`s (`FX-` prefixed
   `tradeId`s) in tests and documentation (inherited GP-Rq-14).
