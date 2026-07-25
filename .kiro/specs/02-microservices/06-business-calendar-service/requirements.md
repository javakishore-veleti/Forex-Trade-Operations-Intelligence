# Requirements Document — Business Calendar (Bounded Context)

> **Inherits `architecture-golden-path/01-service-nfrs`** (all cross-cutting NFRs) and references **Technology Roles** from `01-initial-setup/01-technology-stack`. This spec contains **only business/domain requirements** plus any service-specific narrowing of a golden-path requirement. No product names, versions, or repeated NFRs.

## Introduction

The **Business Calendar** bounded context is the single, authoritative source of calendar truth for the platform. It is realized by the `business-calendar-service` microservice (`Middleware/business-calendar-service/`). The context's single responsibility is **calendar authority**: it answers which days are business days per region, when each region's processing cutoff occurs, which business date a trade or event belongs to, and how daylight-saving-time transitions shift those local-time boundaries — all anchored, for the 24-hour global processing day, to the base country. Because FX processing runs continuously with the base country as the anchor for global-day completion, a small calendar error mis-buckets a trade into the wrong end-of-day; this context exists so no other context has to answer these questions.

Every answer is **deterministic** (per inherited GP-Rq-13): identical inputs and reference-data version yield identical business days, cutoffs, and booking dates, with no model or external inference involved. Cross-cutting concerns (correlation IDs, health/readiness probes, error envelopes, security placeholder, idempotency, optimistic locking, observability, configuration, generic testing) are **inherited from the golden path** and are not restated here. All domain types are sourced from the `shared-domain-contracts` shared kernel. All holiday names are fictional, all time zones are standard IANA references, and all identifiers in examples and tests use the synthetic `FX-` prefix (e.g. `FX-000001`).

---

## Bounded Context and Ubiquitous Language

- **Business Calendar**: This bounded context.
- **RegionCode**: The region enum from the shared kernel (`APAC`, `EMEA`, `AMERICAS`, `GLOBAL`).
- **RegionCalendar**: The calendar definition for a single `RegionCode` — its IANA time zone, weekend definition, holiday set, and `Cutoff`; the consistency boundary for calendar answers about that region.
- **BusinessDay**: A calendar day, evaluated in a region's local time zone, that is neither a weekend day nor a recognized `Holiday` for that region.
- **Holiday**: A configured non-business calendar date scoped to a specific `RegionCode` (fictional or standard reference only).
- **Cutoff**: The local time-of-day after which a region no longer accepts trades or events for the current business date.
- **BookingDate**: The business date to which a trade or event is assigned, derived from its instant, the region's time zone, and the region's `Cutoff`.
- **GlobalBusinessDate**: The 24-hour global processing day anchored to the base-country time zone, used for global consolidation.
- **Spring-forward gap**: A local time that does not exist because clocks advance across a DST transition.
- **Fall-back overlap**: A local time that occurs twice because clocks are set back across a DST transition.
- **IANA time-zone-aware date/time computation**: Local-time boundary resolution that applies the UTC offset actually in effect at the specific instant under a named IANA time zone, so offsets track DST rather than being fixed.

**Persistence roles used** (declares this service's readiness dependencies for golden-path GP-Rq-4): `RELATIONAL_STORE` (region calendar definitions, holidays, and cutoffs — loaded at startup; readiness reports `UP` only when it is reachable and calendar definitions have loaded).

---

## Requirements

### Requirement 1: Regional Calendar Definition

**User Story:** As a platform service that must respect regional calendars, I want each region's business calendar defined with its time zone and weekend rule, so that business-day questions are answered consistently for every region.

#### Acceptance Criteria

1. THE Business Calendar context SHALL maintain one `RegionCalendar` per operational `RegionCode` (`APAC`, `EMEA`, `AMERICAS`), each associated with a valid IANA time zone (e.g. `Asia/Singapore`, `Europe/London`, `America/New_York`).
2. THE context SHALL define the `GLOBAL` calendar as anchored to the base-country time zone (`America/New_York`) for `GlobalBusinessDate` computation.
3. THE context SHALL persist `RegionCalendar` definitions, holidays, and cutoffs in the `RELATIONAL_STORE` and load them at startup.
4. THE context SHALL treat Saturday and Sunday, evaluated in the region's local time zone, as non-business days by default, unless a `RegionCalendar` overrides the weekend definition.
5. THE context SHALL use only fictional or standard-reference holiday and region data; it SHALL NOT embed any proprietary or employer-specific calendar.

---

### Requirement 2: Regional Holiday Rules

**User Story:** As a calendar administrator, I want to configure holidays scoped per region, so that a holiday is excluded from business-day and cutoff calculations only for the region it belongs to.

#### Acceptance Criteria

1. THE context SHALL allow zero or more `Holiday` dates to be configured per `RegionCode`.
2. WHEN evaluating whether a date is a `BusinessDay` for a region, THE context SHALL classify it as a non-business day if that date is a configured `Holiday` for that region.
3. THE context SHALL scope holidays per region so that a `Holiday` in one region does not affect business-day evaluation in another region.
4. THE context SHALL expose a capability returning the configured holidays for a given region and calendar year.
5. WHEN a holiday configuration or query references a value that is not a valid `RegionCode`, THE context SHALL reject it as a validation failure (per inherited GP-Rq-3).

---

### Requirement 3: DST-Aware Time Handling

**User Story:** As a platform operator, I want cutoff and booking calculations to remain correct across daylight-saving transitions, so that trades are not mis-bucketed on the days clocks change.

#### Acceptance Criteria

1. THE context SHALL compute all local-time boundaries using an IANA time-zone-aware date/time computation bound to the region's named time zone, so that UTC offsets are resolved with DST awareness.
2. WHEN a region undergoes a DST transition, THE context SHALL apply the UTC offset in effect at the specific instant being evaluated, never a fixed offset.
3. WHEN a local time falls in a spring-forward gap (a time that does not exist), THE context SHALL resolve it per a documented, deterministic rule.
4. WHEN a local time falls in a fall-back overlap (a time that occurs twice), THE context SHALL resolve it per a documented, deterministic rule.
5. THE context SHALL NOT store or compute cutoffs as fixed UTC offsets that would drift across DST boundaries.

---

### Requirement 4: Business-Day Classification and Arithmetic

**User Story:** As a downstream service, I want to ask whether a date is a business day for a region and to compute business-day offsets and spans, so that validation and settlement logic can rely on a single calendar authority.

#### Acceptance Criteria

1. THE context SHALL expose a capability that classifies a given date for a region as a `BusinessDay` or not, returning the reason (`WEEKEND`, `HOLIDAY`, or `BUSINESS_DAY`).
2. THE context SHALL expose an **add-business-days** capability returning the date that is `n` business days after (or before, for negative `n`) a given date, skipping weekends and holidays for that region.
3. THE context SHALL expose a **business-days-between** capability returning the count of business days in the half-open interval `[from, to)` for that region.
4. WHEN a query references a region with no configured `RegionCalendar`, THE context SHALL report an unknown-resource result identifying the missing region calendar (per inherited GP-Rq-1/GP-Rq-3).

---

### Requirement 5: Regional Cutoff and Booking-Date Classification

**User Story:** As the trade-ingest and lifecycle contexts, I want to know which business date a trade or event belongs to given its instant, so that late events are correctly assigned to the next business date rather than silently mis-bucketed.

#### Acceptance Criteria

1. THE context SHALL maintain a configurable local-time `Cutoff` per `RegionCode`.
2. THE context SHALL expose a **booking-date** capability returning the `BookingDate` for a given instant in a given region.
3. WHEN an instant, converted to the region's local time, falls at or before the region's `Cutoff` on a `BusinessDay`, THE context SHALL classify its `BookingDate` as that business date.
4. WHEN an instant, converted to the region's local time, falls after the region's `Cutoff`, or on a non-business day, THE context SHALL classify its `BookingDate` as the next `BusinessDay`.
5. THE context SHALL expose an **is-post-cutoff** capability returning whether a given instant is after the current business date's `Cutoff` for that region.
6. THE context SHALL expose a **global-business-date** capability returning the `GlobalBusinessDate` for a given instant, anchored to the base-country time zone.

---

### Requirement 6: Domain Acceptance Scenarios

**User Story:** As a QA engineer, I want the calendar-specific behaviors pinned by tests, so that the domain invariants never regress at DST boundaries, cutoffs, and holidays. (General testing standards are inherited from GP-Rq-12; these are the domain scenarios that must be covered.)

#### Acceptance Criteria

1. THE context SHALL be covered by a test asserting `BusinessDay` classification for a weekend day, a configured `Holiday`, and an ordinary business day, across at least two regions with different IANA time zones.
2. THE context SHALL be covered by a test asserting `BookingDate` classification for an instant just before the `Cutoff`, an instant just after the `Cutoff`, and an instant on a `Holiday`, for at least two regions.
3. THE context SHALL be covered by a test asserting correct behavior across a spring-forward gap and a fall-back overlap for at least one region, confirming boundaries resolve per the documented deterministic rule.
4. THE context SHALL be covered by a test asserting that identical inputs and reference-data version yield identical calendar answers (the domain view of inherited GP-Rq-13 determinism) for business-day, cutoff, and booking-date queries.
5. EVERY scenario SHALL use only fictional holidays, standard IANA time zones, and synthetic `FX-` prefixed identifiers.
