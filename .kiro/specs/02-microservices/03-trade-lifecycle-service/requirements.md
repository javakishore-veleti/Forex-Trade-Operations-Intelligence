# Requirements Document — Trade Lifecycle (Bounded Context)

> **Inherits `architecture-golden-path/01-service-nfrs`** (all cross-cutting NFRs) and references **Technology Roles** from `01-initial-setup/01-technology-stack`. This spec contains **only business/domain requirements** plus any service-specific narrowing of a golden-path requirement. No product names, versions, or repeated NFRs.

## Introduction

The **Trade Lifecycle** bounded context owns the authoritative lifecycle state of every FX trade. It is realized by the `trade-lifecycle-service` microservice (`Middleware/trade-lifecycle-service/`). The context's single responsibility is **state management**: it advances a trade through its lifecycle in response to domain events, enforces which transitions are valid, and preserves an immutable history of what happened. It does **not** calculate risk, evaluate business rules, or move money — those are other bounded contexts.

Cross-cutting concerns (correlation IDs, health probes, error envelopes, security placeholder, event atomicity, idempotent consumption, observability, testing standards, determinism) are **inherited from the golden path** and are not restated here. All identifiers in examples and tests use the synthetic `FX-` prefix (e.g. `FX-000001`); all names are fictional.

---

## Bounded Context and Ubiquitous Language

- **Trade Lifecycle**: This bounded context.
- **Lifecycle Aggregate**: The consistency boundary for a single trade's current status and its transition history; identified by `tradeId`.
- **Trade Status**: The `TradeStatus` enum from the `shared-domain-contracts` shared kernel.
- **Lifecycle Transition**: A movement of the `Lifecycle Aggregate` from one status to another, induced by a consumed domain event.
- **State Machine**: The domain invariant defining which transitions are valid.
- **Illegal Transition**: An attempted transition the `State Machine` forbids.
- **Lifecycle Audit History**: The immutable, append-only sequence of every transition (and rejected/no-op attempt) for a trade.
- **Expected Lifecycle**: The canonical ordered status sequence a trade follows under normal processing.
- **Orphan Event**: A domain event referencing a trade the context has never seen, where the event is not the lifecycle-initiating event.

**Consumed domain events** (from the shared kernel): `TradeEventType` values from the `EVENT_STREAM`.
**Persistence roles used** (declares this service's readiness dependencies for golden-path GP-Rq-4): `RELATIONAL_STORE` (current state), `DOCUMENT_STORE` (audit history), `EVENT_STREAM` (event consumption), `CACHE` (event deduplication).

---

## Requirements

### Requirement 1: Lifecycle State Machine Invariant

**User Story:** As a platform architect, I want one authoritative definition of valid lifecycle transitions, so that a trade's state can only change in valid, auditable ways regardless of which event arrives.

#### Acceptance Criteria

1. THE Trade Lifecycle context SHALL define a `State Machine` whose permitted forward transitions are exactly: `CAPTURED → VALIDATED → ENRICHED → RISK_CALCULATED → BOOKED → ALLOCATED → CONFIRMED → SETTLED`.
2. THE `State Machine` SHALL permit `→ CANCELLED` from any of `{CAPTURED, VALIDATED, ENRICHED, RISK_CALCULATED, BOOKED, ALLOCATED, CONFIRMED}`.
3. THE `State Machine` SHALL permit `→ AMENDED` from any of `{CAPTURED, VALIDATED, ENRICHED, RISK_CALCULATED, BOOKED}`.
4. THE `State Machine` SHALL permit `→ FAILED` from any non-terminal status.
5. THE `State Machine` SHALL treat `SETTLED`, `CANCELLED`, and `FAILED` as terminal (no further forward transition).
6. THE `State Machine` SHALL map each consumed `TradeEventType` to the status it induces (e.g. `TRADE_VALIDATED → VALIDATED`, `RISK_CALCULATION_COMPLETED → RISK_CALCULATED`, `TRADE_BOOKED → BOOKED`, `TRADE_SETTLED → SETTLED`, `TRADE_CANCELLED → CANCELLED`, `TRADE_AMENDED → AMENDED`, `TRADE_FAILED → FAILED`).

---

### Requirement 2: Event-Driven State Advancement

**User Story:** As a downstream context, I want a trade's state to advance automatically as domain events occur, so that current state reflects real processing progress.

#### Acceptance Criteria

1. WHEN a consumed event induces a status that is a permitted transition from the aggregate's current status, THE context SHALL advance the `Lifecycle Aggregate` to that status.
2. WHEN a `TRADE_CAPTURED` event is consumed for an unknown `tradeId`, THE context SHALL initialize a new `Lifecycle Aggregate` in status `CAPTURED`.
3. WHEN any non-initiating event is consumed for an unknown `tradeId`, THE context SHALL record it as an `Orphan Event` in the `Lifecycle Audit History` and SHALL NOT create an aggregate.
4. WHEN a transition succeeds, THE context SHALL commit the current-state change and its audit record together (per inherited GP-Rq-7 atomicity).

---

### Requirement 3: Illegal Transition Enforcement

**User Story:** As a platform operator, I want invalid transitions rejected and recorded, so that out-of-order or stray events cannot corrupt trade state.

#### Acceptance Criteria

1. WHEN a consumed event would induce an `Illegal Transition`, THE context SHALL leave the aggregate's current status unchanged.
2. WHEN an `Illegal Transition` is detected, THE context SHALL append a `rejected` entry to the `Lifecycle Audit History` capturing the current status, the attempted status, and the triggering event identity.
3. THE context SHALL continue processing subsequent events after a rejection (a single bad event does not halt the context).
4. IF a consumed event targets a status equal to the aggregate's current status, THEN THE context SHALL treat it as a no-op duplicate (recorded as such) rather than an `Illegal Transition`. (Duplicate delivery itself is handled by inherited GP-Rq-5.)

---

### Requirement 4: Immutable Lifecycle Audit History

**User Story:** As an auditor, I want an immutable, ordered history of every lifecycle event for a trade, so that I can reconstruct exactly what happened and when.

#### Acceptance Criteria

1. THE context SHALL append one `Lifecycle Audit History` entry to the `DOCUMENT_STORE` for every processed event — whether it results in a transition, a rejection, a no-op, or an orphan.
2. EACH entry SHALL record: `tradeId`, `correlationId`, event identity and type, `fromStatus`, `toStatus`, the flags `rejected`/`noop`/`orphan`, `sourceService`, event-time (`occurredAt`), and ingestion-time (`recordedAt`).
3. THE context SHALL treat audit entries as append-only (never updated or deleted).
4. WHEN audit history is read for a `tradeId`, THE context SHALL return entries ordered by `occurredAt`, with `recordedAt` as a deterministic tie-breaker.

---

### Requirement 5: Lifecycle Query Capabilities

**User Story:** As a downstream service or investigator, I want to read a trade's current state and full timeline, so that I can answer "what state is this in?" and "what happened to it?".

#### Acceptance Criteria

1. THE context SHALL expose a query returning a trade's **current status** and last-updated instant, or "unknown trade" when absent.
2. THE context SHALL expose a query returning a trade's **full lifecycle timeline** (the ordered `Lifecycle Audit History`), with `rejected`/`noop`/`orphan` entries visible so anomalies are not hidden.
3. THE context SHALL expose a query returning the **`Expected Lifecycle`** sequence alongside observed statuses, marking each expected status as `reached` or `pending`.

---

### Requirement 6: Amendment and Cancellation

**User Story:** As a trade operations analyst, I want amendments and cancellations reflected in state and history, so that the trade record stays accurate after post-capture changes.

#### Acceptance Criteria

1. WHEN a `TRADE_CANCELLED` event is consumed for a trade in a cancel-permitted status, THE context SHALL transition it to `CANCELLED`, recording the prior status.
2. WHEN a `TRADE_AMENDED` event is consumed for a trade in an amend-permitted status, THE context SHALL transition it to `AMENDED`, recording the amendment.
3. WHEN a cancel/amend event is consumed for a trade already in a terminal status, THE context SHALL treat it as an `Illegal Transition` per Requirement 3.
4. THE context SHALL record the state change only; it SHALL NOT compute downstream ripple effects of the amendment/cancellation (that is a later agent context).

---

### Requirement 7: Domain Acceptance Scenarios

**User Story:** As a QA engineer, I want the lifecycle-specific behaviors pinned by tests, so that the domain invariants never regress. (General testing standards are inherited from GP-Rq-12; these are the domain scenarios that must be covered.)

#### Acceptance Criteria

1. THE context SHALL be covered by a test asserting every permitted transition in Requirement 1 succeeds and a representative set of illegal transitions are rejected.
2. THE context SHALL be covered by an end-to-end test that consumes the ordered sequence `TRADE_CAPTURED → … → TRADE_SETTLED` and asserts the final current status is `SETTLED` and the timeline contains the full ordered history.
3. THE context SHALL be covered by a test asserting that re-consuming an already-processed event produces no additional transition and a `noop` audit entry (domain view of inherited GP-Rq-5).
4. THE context SHALL be covered by a test asserting an `Orphan Event` is recorded without creating an aggregate.
