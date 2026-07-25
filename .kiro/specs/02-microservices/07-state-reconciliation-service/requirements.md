# Requirements Document — State Reconciliation (Bounded Context)

> **Inherits `architecture-golden-path/01-service-nfrs`** (all cross-cutting NFRs) and references **Technology Roles** from `01-initial-setup/01-technology-stack`. This spec contains **only business/domain requirements** plus any service-specific narrowing of a golden-path requirement. No product names, versions, or repeated NFRs.

## Introduction

The **State Reconciliation** bounded context is the platform's **deterministic canonical-state authority and cross-source divergence detector**. It is realized by the `state-reconciliation-service` microservice (`Middleware/state-reconciliation-service/`). Its single responsibility is to **decide**, for a given trade, the one authoritative state the trade *should* be in, to read what each system of record *actually* reports, and to expose exactly where and how those disagree — together with which invariants are violated and which corrective actions are permitted. The context **decides**; a later agent context **interprets and coordinates**.

The same trade can appear as `BOOKED` in the `RELATIONAL_STORE`, `RISK_CALCULATED` in the `DOCUMENT_STORE`, `PENDING` in the `CACHE`, and — per the latest event on the `EVENT_STREAM` — `TRADE_CANCELLED`. That is a **business-state inconsistency**, not merely a technical one, and resolving it authoritatively is this context's reason to exist.

It does **not** own or advance lifecycle state (that is the Trade Lifecycle context), it does **not** compute monetary exposure (that is the risk/exposure context), it does **not** determine canonical state by majority vote or by any model, and it **never executes** a corrective action — it only reports which actions are permitted.

Cross-cutting concerns (correlation IDs, error envelopes, health/liveness probes, security placeholder, idempotency, optimistic locking, observability, configuration, resilience, testing standards, and the determinism/LLM boundary of GP-Rq-13) are **inherited from the golden path** and are not restated here. This spec **narrows GP-Rq-4** for degraded readiness. All identifiers in examples and tests use the synthetic `FX-` prefix (e.g. `FX-000001`); all names are fictional.

---

## Bounded Context and Ubiquitous Language

- **State Reconciliation**: This bounded context.
- **System of Record (Source)**: One participating store whose trade state is read during reconciliation. The reconciled sources are the `RELATIONAL_STORE` (current trade row), the `DOCUMENT_STORE` (trade document / latest audit status), the `CACHE` (cached runtime state), and the latest domain event for the trade on the `EVENT_STREAM`. An `ANALYTICS_PLATFORM` state is an **optional** additional source when configured.
- **Observed State**: The `TradeStatus` (from the `shared-domain-contracts` shared kernel) that a specific source reports for a trade at reconciliation time, or `UNAVAILABLE` when that source cannot be read.
- **Canonical Expected State**: The single authoritative `TradeStatus` a trade should be in, derived **deterministically** from the trade's ordered domain-event history and the lifecycle `State Machine` — never by majority vote and never by a model.
- **Incomplete History**: A derivation condition in which the ordered event history is missing a required event, so only the furthest canonical state supported by the observed events can be derived.
- **Divergence**: A condition where a source's `Observed State` differs from the `Canonical Expected State`.
- **Divergence Classification**: The kind of a divergence — `STALE` (source behind canonical), `AHEAD` (source ahead of canonical), or `CONFLICTING` (source in a status not reachable on the canonical path).
- **Most-Likely-Stale Source**: For a set of divergences, the source most likely behind or incorrect, judged deterministically from source timestamps and the canonical path.
- **Invariant**: A cross-source business rule that must always hold (e.g. "a `SETTLED` trade must not appear `PENDING` in the `CACHE`").
- **Violated Invariant**: An `Invariant` found broken during reconciliation, reported with a stable code and description.
- **Permitted Action**: A corrective action drawn from a **fixed, enumerated catalogue** (e.g. `REFRESH_CACHE`, `REPLAY_EVENT`, `OPEN_RECONCILIATION_CASE`) that the platform deterministically allows for a given divergence — never free-form or model-authored, and never executed by this context.
- **Business Impact**: A deterministic ordinal severity assigned to a reconciliation outcome.
- **Reconciliation Result**: The response envelope carrying observed states, canonical expected state, violated invariants, permitted actions, and business impact.

**Consumed domain events** (from the shared kernel): `TradeEventType` values read from the `EVENT_STREAM`.
**Persistence roles read** (declares this service's readiness dependencies for the narrowed GP-Rq-4): `RELATIONAL_STORE`, `DOCUMENT_STORE`, `CACHE`, `EVENT_STREAM`, and optionally `ANALYTICS_PLATFORM`. This context is **read-only across every source**: it reads state to reconcile it and SHALL NOT modify any source.

---

## Requirements

### Requirement 1: Cross-Source Observed-State Retrieval

**User Story:** As a reconciliation consumer, I want the context to read a trade's state from every participating system of record, so that divergence is detected against the complete picture rather than a single store.

#### Acceptance Criteria

1. THE State Reconciliation context SHALL read the `Observed State` for a trade from each source: the `RELATIONAL_STORE` (current trade row), the `DOCUMENT_STORE` (trade document / latest audit status), the `CACHE` (cached state), and the latest domain event for the trade on the `EVENT_STREAM`.
2. FOR each `Observed State`, THE context SHALL record the source, the observed `TradeStatus`, and the source timestamp where available.
3. WHEN a source is unreachable at reconciliation time, THE context SHALL record that source as `UNAVAILABLE` rather than failing the whole reconciliation, and SHALL include the unavailability in the result.
4. THE context SHALL retrieve state read-only and SHALL NOT modify any source during reconciliation.
5. WHERE an `ANALYTICS_PLATFORM` state source is configured, THE context SHALL include it as an additional optional `Observed State`.

---

### Requirement 2: Deterministic Canonical Expected State Derivation

**User Story:** As a reconciliation consumer, I want the canonical expected state derived deterministically from event history, so that divergence is measured against an authoritative reference rather than a majority vote among possibly-stale systems.

#### Acceptance Criteria

1. THE context SHALL derive the `Canonical Expected State` for a trade deterministically from the trade's ordered domain-event history and the lifecycle `State Machine` (the same transition rules the Trade Lifecycle context enforces).
2. THE context SHALL NOT determine the canonical state by majority vote across sources, and SHALL NOT delegate the decision to a model or agent (the determinism/LLM boundary of GP-Rq-13 applies).
3. WHEN the ordered event history is incomplete (a required event is missing), THE context SHALL derive the furthest canonical state supported by the observed events and mark the derivation `INCOMPLETE_HISTORY`.
4. WHEN the same trade is reconciled twice against identical underlying data, THE context SHALL derive an identical `Canonical Expected State` (the domain determinism guarantee of GP-Rq-13: identical data yields identical canonical state).

---

### Requirement 3: Divergence Detection and Classification

**User Story:** As a reconciliation consumer, I want each source's observed state compared against the canonical expected state, so that I can see exactly which sources disagree and how.

#### Acceptance Criteria

1. THE context SHALL compare each `Observed State` against the `Canonical Expected State` and flag a `Divergence` for every source whose observed status differs.
2. THE context SHALL classify each `Divergence` as `STALE` (source behind canonical), `AHEAD` (source ahead of canonical), or `CONFLICTING` (source in a status not reachable on the canonical path).
3. FOR the detected divergences, THE context SHALL identify the `Most-Likely-Stale Source` deterministically from source timestamps and the canonical path.
4. WHEN every source agrees with the `Canonical Expected State`, THE context SHALL report zero divergences and an overall status of `CONSISTENT`.

---

### Requirement 4: Invariant Evaluation and Permitted Actions

**User Story:** As a platform controller, I want violated cross-source invariants and the deterministically-permitted corrective actions returned, so that any corrective step an agent later proposes is drawn from a controlled, approved set — never invented.

#### Acceptance Criteria

1. THE context SHALL evaluate a configured set of cross-source `Invariant`s against the observed states and the canonical state, and SHALL return every `Violated Invariant` with a stable code and description.
2. THE context SHALL compute the set of `Permitted Action`s for the detected divergences deterministically (e.g. a stale `CACHE` entry permits `REFRESH_CACHE`; a missing downstream event permits `REPLAY_EVENT`; an unresolved conflict permits `OPEN_RECONCILIATION_CASE`).
3. THE context SHALL restrict `Permitted Action`s to a fixed, enumerated catalogue and SHALL NOT emit free-form or natural-language actions.
4. THE context SHALL determine `Permitted Action`s with deterministic policy logic only and SHALL NOT allow a model to expand the permitted-action set (GP-Rq-13 boundary).
5. THE context SHALL NOT execute any `Permitted Action`; it only reports which actions are permitted (execution is coordinated, under human approval, in a later agent context).

---

### Requirement 5: Reconciliation Result Contract

**User Story:** As the later Business-State Divergence agent context, I want a stable, machine-readable reconciliation envelope, so that I can interpret and coordinate resolution without re-deriving canonical state myself.

#### Acceptance Criteria

1. THE context SHALL expose a query returning a `Reconciliation Result` envelope for a single `tradeId`.
2. THE `Reconciliation Result` SHALL contain at minimum: `states` (map of source → observed status + timestamp, including any `UNAVAILABLE` source), `expectedState` (the `Canonical Expected State`), `violatedInvariants` (array), `permittedActions` (array), and a `businessImpact` classification.
3. THE `Reconciliation Result` field shape SHALL be compatible with the `PRD.md` contract (`states`, `expectedState`, `violatedInvariants`, `permittedActions`), extended with `businessImpact`.
4. THE context SHALL expose a batch/on-demand sweep that accepts a set of `tradeId`s (or a filter) and returns one `Reconciliation Result` per trade.

---

### Requirement 6: Business Impact Classification

**User Story:** As a risk and operations consumer, I want each reconciliation outcome assigned a deterministic business-impact severity, so that results can be prioritized without an agent guessing severity.

#### Acceptance Criteria

1. THE context SHALL classify `businessImpact` deterministically from the nature of the divergence and the trade's canonical state (e.g. a settlement-stage conflict outranks a cache lag on an early-stage trade).
2. THE context SHALL express `businessImpact` on a fixed ordinal scale (e.g. `NONE`, `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`).
3. THE context SHALL NOT compute monetary exposure itself; where impact depends on exposure, it SHALL reference the risk/exposure context rather than calculating figures locally.

---

### Requirement 7: Degraded Readiness (overrides GP-Rq-4)

**User Story:** As an orchestrator, I want reconciliation to remain reachable when a single source is unavailable, so that partial-picture reconciliation is still served instead of the whole service dropping out of rotation.

#### Acceptance Criteria

1. WHERE exactly one reconciled source (`RELATIONAL_STORE`, `DOCUMENT_STORE`, `CACHE`, or `EVENT_STREAM`) is unavailable, THE context SHALL report **degraded readiness** — remaining ready to serve reconciliation with that source recorded `UNAVAILABLE` per Requirement 1.3 — rather than reporting fully not-ready. This narrows the strict all-dependencies-reachable rule of GP-Rq-4.
2. WHEN more than one reconciled source is unavailable, THE readiness behaviour SHALL fall back to the golden-path GP-Rq-4 not-ready semantics.

---

### Requirement 8: Domain Acceptance Scenarios

**User Story:** As a QA engineer, I want the reconciliation-specific behaviours pinned by tests, so that the canonical authority and the permitted-action catalogue never regress. (General testing standards are inherited from GP-Rq-12; these are the domain scenarios that must be covered.)

#### Acceptance Criteria

1. THE context SHALL be covered by a test asserting that when every source agrees with the `Canonical Expected State`, the result is `CONSISTENT` with zero divergences.
2. THE context SHALL be covered by a test of the canonical divergence scenario — `RELATIONAL_STORE`=`BOOKED`, `DOCUMENT_STORE`=`RISK_CALCULATED`, `CACHE`=`PENDING`, and the latest `EVENT_STREAM` event = `TRADE_CANCELLED` — asserting the correct `Canonical Expected State` (`expectedState`), the correct per-source `Divergence Classification`, the expected `Violated Invariant`s, and that every returned action is drawn only from the `Permitted Action` catalogue.
3. THE context SHALL be covered by a test asserting the same trade reconciled twice against identical underlying data yields an identical `Canonical Expected State` (domain view of the GP-Rq-13 determinism guarantee).
4. THE context SHALL be covered by a test asserting the `Reconciliation Result` envelope shape matches the Requirement 5 contract, including a source recorded `UNAVAILABLE` when one source cannot be read.
