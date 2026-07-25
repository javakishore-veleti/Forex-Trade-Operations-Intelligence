# Requirements Document — End-of-Day Processing (Bounded Context)

> **Inherits `architecture-golden-path/01-service-nfrs`** (all cross-cutting NFRs) and references **Technology Roles** from `01-initial-setup/01-technology-stack`. This spec contains **only business/domain requirements** plus any service-specific narrowing of a golden-path requirement. No product names, versions, or repeated NFRs.

## Introduction

The **End-of-Day Processing** bounded context owns the orchestration of the daily close for globally distributed FX operations. It is realized by the `eod-processing-service` microservice (`Middleware/eod-processing-service/`). The context's single responsibility is **close orchestration**: it tracks each region's readiness to close, aligns every regional close to one global business day anchored on the base country, and consolidates the global end-of-day result only when every prerequisite region is ready.

It does **not** compute risk (that is the **Risk Calculation** context) and it does **not** decide calendars or classify booking dates (that is the **Business Calendar** context) — it consumes those contexts' outputs and coordinates deterministic readiness state around them. The readiness and consolidation gates are fully deterministic; no model participates (inherited GP-Rq-13). A later agent explains and coordinates around this context but never replaces its decisions.

Cross-cutting concerns (correlation IDs, health/readiness probes, error envelopes, security placeholder, idempotency mechanics, optimistic locking, event atomicity, observability, configuration, generic testing standards, determinism) are **inherited from the golden path** and are not restated here. All identifiers in examples and tests use the synthetic `FX-` prefix; all branch and region names are fictional.

---

## Bounded Context and Ubiquitous Language

- **End-of-Day Processing**: This bounded context.
- **Global Business Date**: The single 24-hour global processing day anchored on the base country, obtained from the **Business Calendar** context; this context never derives calendar logic itself.
- **Region**: A close jurisdiction identified by a `RegionCode` from the shared kernel — `APAC`, `EMEA`, `AMERICAS`, and the aggregate `GLOBAL`.
- **Regional Close**: The lifecycle of a region's end-of-day processing for a `Global Business Date`, holding one of `{IN_PROGRESS, BLOCKED, READY, CLOSED}`.
- **Branch**: A fictional processing branch belonging to a region whose completion contributes to that region's readiness.
- **Branch Completion**: The per-`(Global Business Date, Region, Branch)` flag recording whether that branch has finished processing.
- **Regional Readiness**: The deterministic evaluation of whether a region may reach `READY`, derived from branch completion, unprocessed-trade count, EOD risk-snapshot existence, and blocker state.
- **Readiness Status Map**: The projection of each region's `Regional Close` status plus the `GLOBAL` consolidation status (e.g. `APAC: READY, EMEA: BLOCKED, AMERICAS: IN_PROGRESS, GLOBAL: NOT_READY`).
- **Blocker**: An unresolved condition (e.g. an incomplete branch, an untriaged late trade) that prevents a region from reaching `READY`.
- **Late Trade**: A trade whose booking date (as classified by the **Business Calendar** context) falls after a region's close began; a candidate `Blocker`.
- **Exception**: An authorized clearance of a named `Blocker`, valid only when accompanied by a caller-supplied `approvalReference`.
- **Rerun**: A controlled re-evaluation of a region's `Regional Readiness` after a blocker is resolved.
- **Global Consolidation**: The final step that aggregates all regional closes into the global end-of-day result for a `Global Business Date`.

**Consumed domain events** (from the shared kernel via the `EVENT_STREAM`): risk-snapshot-completion and booking-date-classification signals produced by the **Risk Calculation** and **Business Calendar** contexts, used as readiness inputs.
**Produced domain events**: an EOD-completion event published on the `EVENT_STREAM` when `Global Consolidation` succeeds.
**Persistence roles used** (declares this service's readiness dependencies for golden-path GP-Rq-4): `RELATIONAL_STORE` (regional close, branch completion, and consolidation state), `EVENT_STREAM` (readiness input consumption and EOD-completion publication), plus reachability of the **Business Calendar** and **Risk Calculation** peer contexts required to evaluate readiness.

---

## Requirements

### Requirement 1: Global Business Day Alignment

**User Story:** As the platform, I want every regional close aligned to a single global business day anchored on the base country, so that a 24-hour global FX day is consolidated consistently regardless of regional time zones.

#### Acceptance Criteria

1. THE End-of-Day Processing context SHALL obtain the current `Global Business Date` from the **Business Calendar** context rather than computing calendar logic itself.
2. THE context SHALL associate every `Regional Close` and `Global Consolidation` record with a specific `Global Business Date`.
3. THE context SHALL evaluate regions in the order in which they close relative to the base country (by default `APAC`, then `EMEA`, then `AMERICAS`), while permitting that ordering to be configured (inherited GP-Rq-11).
4. WHEN regional data spans a daylight-saving or date-line boundary, THE context SHALL rely on the **Business Calendar** context's booking-date classification to assign trades and events to the correct `Global Business Date`.

---

### Requirement 2: Branch Completion Tracking

**User Story:** As an EOD operator, I want each region's branch completion tracked, so that a region is considered ready only when all of its branches have finished processing.

#### Acceptance Criteria

1. THE context SHALL maintain one `Branch Completion` record per (`Global Business Date`, `Region`, `Branch`) in the `RELATIONAL_STORE`.
2. THE context SHALL expose an operation to mark a branch complete for the current `Global Business Date`.
3. THE context SHALL expose a read returning each branch in a region and whether it is complete for the current `Global Business Date`.
4. WHILE any branch in a region is not marked complete, THE context SHALL NOT allow that region to reach `READY`.
5. THE context SHALL treat branch completion as idempotent per the inherited idempotency guarantee (GP-Rq-5): re-marking an already-complete branch SHALL have no additional effect.

---

### Requirement 3: Deterministic Regional Readiness Evaluation

**User Story:** As an EOD operator, I want each region's readiness computed deterministically from its inputs, so that I see a clear, explainable READY / BLOCKED status per region.

#### Acceptance Criteria

1. THE context SHALL compute `Regional Readiness` deterministically from all of: every branch in the region complete; unprocessed-trade count is zero (or below a configured tolerance covered by an approved `Exception`); the region's EOD risk snapshot exists in the **Risk Calculation** context; and no unresolved `Blocker` exists.
2. WHEN all readiness inputs for a region are satisfied, THE context SHALL set that region's `Regional Close` to `READY`.
3. WHEN one or more readiness inputs are unsatisfied, THE context SHALL set that region's `Regional Close` to `BLOCKED` and record the specific unmet conditions.
4. THE context SHALL expose a read returning the `Readiness Status Map` across all regions together with the `GLOBAL` consolidation status.
5. THE context SHALL expose a read returning the current list of `Blocker` conditions for a region.

---

### Requirement 4: Late Trade and Blocker Handling

**User Story:** As an EOD operator, I want late trades and blockers tracked against the region they affect, so that consolidation cannot proceed while material blockers remain.

#### Acceptance Criteria

1. THE context SHALL track `Late Trade`s (those whose booking date, per the **Business Calendar** context, falls after a region's close began) as candidate `Blocker`s for that region.
2. THE context SHALL NOT itself decide the materiality of a `Late Trade`; it SHALL record and expose the late trade, deferring materiality classification to the **Risk Calculation** context, the rules policy, and later agents.
3. WHILE a `Blocker` is recorded for a region, THE context SHALL keep that region `BLOCKED` until the `Blocker` is resolved or an authorized `Exception` clears it (Requirement 5).
4. THE context SHALL expose an operation to record an authorized `Exception` that clears a named `Blocker`, capturing the caller-supplied `approvalReference`.

---

### Requirement 5: Rerun and Exception Approval Gate

**User Story:** As an EOD operator, I want to rerun a region's readiness evaluation and to apply approved exceptions, so that a region can progress after a blocker is resolved without corrupting the global date.

#### Acceptance Criteria

1. THE context SHALL expose a `Rerun` operation that re-evaluates `Regional Readiness` for the current `Global Business Date` and returns the updated status.
2. WHEN an `Exception` is applied, THE context SHALL require a non-blank `approvalReference` and SHALL reject the request with a validation error (per inherited GP-Rq-3) when it is absent.
3. THE context SHALL record every `Rerun` and every applied `Exception` in an append-only EOD audit log capturing `Region`, `Global Business Date`, `approvalReference` (where applicable), and timestamp.
4. THE context SHALL NOT auto-approve an `Exception`: a `Blocker` is cleared by an `Exception` only when an explicit `approvalReference` is supplied by the caller (the human-approval gate is enforced upstream in a later agent phase).

---

### Requirement 6: Global Consolidation Guard

**User Story:** As the platform, I want global consolidation to start only when every prerequisite region is ready, so that the global end-of-day result is never computed on incomplete regional data.

#### Acceptance Criteria

1. THE context SHALL expose an operation that triggers `Global Consolidation` for the current `Global Business Date`.
2. WHEN `Global Consolidation` is requested while any prerequisite region is not `READY`, THE context SHALL reject the request with a conflict response (per inherited GP-Rq-3) whose body lists the not-ready regions and their blockers.
3. WHEN all prerequisite regions are `READY`, THE context SHALL perform consolidation, set each region's `Regional Close` to `CLOSED`, set `GLOBAL` to `CLOSED`, and publish an EOD-completion event to the `EVENT_STREAM` (committed atomically with the state change per inherited GP-Rq-7).
4. THE context SHALL make `Global Consolidation` idempotent per `Global Business Date` (per inherited GP-Rq-5): a repeated consolidation for an already-`CLOSED` global date SHALL return the existing result without re-executing.
5. THE context SHALL record the consolidation outcome, the contributing regional snapshots, and any applied `Exception`s in the EOD audit log.

---

### Requirement 7: Domain Acceptance Scenarios

**User Story:** As a QA engineer, I want the EOD-specific gating behaviors pinned by tests, so that the readiness and consolidation invariants never regress. (General testing standards are inherited from GP-Rq-12; these are the domain scenarios that must be covered.)

#### Acceptance Criteria

1. THE context SHALL be covered by a test asserting a region reaches `READY` only when all `Regional Readiness` inputs of Requirement 3 are satisfied, and is `BLOCKED` otherwise.
2. THE context SHALL be covered by a test asserting that requesting `Global Consolidation` returns a conflict while any region is not `READY`, and succeeds only when every prerequisite region is `READY`.
3. THE context SHALL be covered by a test asserting `Global Consolidation` is idempotent per `Global Business Date`: a repeat for an already-`CLOSED` date returns the existing result without re-executing (Requirement 6.4).
4. THE context SHALL be covered by a test asserting that applying an `Exception` without an `approvalReference` is rejected (Requirement 5.2).
