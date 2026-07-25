# Requirements Document — Trade Capture (Bounded Context)

> **Inherits `architecture-golden-path/01-service-nfrs`** (all cross-cutting NFRs) and references **Technology Roles** from `01-initial-setup/01-technology-stack`. This spec contains **only business/domain requirements** plus any service-specific narrowing of a golden-path requirement. No product names, versions, or repeated NFRs.

## Introduction

The **Trade Capture** bounded context is the **front door** through which every new FX trade enters the platform. It is realized by the `trade-ingest-service` microservice (`Middleware/trade-ingest-service/`). The context's single responsibility is **capture**: it accepts a submitted trade request, validates it against the trade-capture business rules, assigns it an authoritative trade identity, persists it as a captured trade, and publishes the `TradeCaptured` domain event so downstream contexts can begin processing.

It does **not** advance the trade's lifecycle beyond capture, calculate risk, enrich, book, allocate, confirm, or settle — those belong to other bounded contexts. It is the only context that mints a new `tradeId` and asserts a trade for the first time.

Cross-cutting concerns (correlation IDs, health probes, structured error envelopes, security placeholder, event-publish atomicity, optimistic locking, generic idempotency mechanics, observability, resilience, configuration, and testing standards) are **inherited from the golden path** and are not restated here. All identifiers in examples and tests use the synthetic `FX-` prefix (e.g. `FX-000001`); all counterparty, book, region, and organization names are fictional.

---

## Bounded Context and Ubiquitous Language

- **Trade Capture**: This bounded context.
- **Trade Request**: The inbound submission carrying all fields required to assert a new FX trade (currency pair, notional amount and currency, direction, trade date, value date, counterparty, trading book, region).
- **Captured Trade**: The `Capture Aggregate` — the consistency boundary for a newly asserted trade at status `CAPTURED`; identified by its assigned `tradeId`.
- **Trade Identity (`tradeId`)**: The authoritative business key minted by this context, of the form `FX-` followed by a zero-padded six-digit sequence (e.g. `FX-000001`); distinct from any store-internal surrogate key.
- **Currency Pair**: A `BASE/QUOTE` pairing whose `pairCode` is two three-letter currency codes joined by `/`.
- **Trade Direction**: The enum from the `shared-domain-contracts` shared kernel; permitted values `BUY`, `SELL`.
- **Region Code**: The enum from the shared kernel; permitted values `APAC`, `EMEA`, `AMERICAS`, `GLOBAL`.
- **Trade Status**: The `TradeStatus` enum from the shared kernel; a newly asserted trade carries status `CAPTURED`.
- **Business Day**: A calendar day that is neither Saturday, Sunday, nor a recognized public holiday per the context's business-calendar logic.
- **Exactly-Once Capture**: The domain guarantee that one submission intent yields exactly one `Captured Trade` — a replayed submission returns the original captured trade and publishes no second `TradeCaptured` event (domain view of inherited GP-Rq-5).

**Produced domain event** (into the shared kernel `EVENT_STREAM`): `TradeCaptured` — the `TradeEventType` this context emits when a trade is first asserted, carrying the assigned `tradeId` and the captured trade fields.
**Persistence roles used** (declares this service's readiness dependencies for golden-path GP-Rq-4): `RELATIONAL_STORE` (captured-trade record of record), `CACHE` (exactly-once capture markers), `EVENT_STREAM` (`TradeCaptured` publication).

---

## Requirements

### Requirement 1: Trade Capture Endpoint

**User Story:** As a trade-submitting system, I want to submit an FX trade and receive a confirmation with its assigned identity, so that the trade is recorded and downstream processing can begin.

#### Acceptance Criteria

1. THE Trade Capture context SHALL expose a state-changing capture operation that accepts a `Trade Request` and, on success, asserts a new `Captured Trade`.
2. WHEN a `Trade Request` satisfies every capture validation rule (Requirement 2) and is not a replay, THE context SHALL persist the `Captured Trade` at status `CAPTURED`, publish the `TradeCaptured` event, and return a successful-creation confirmation carrying the assigned `tradeId`, the resolved `CorrelationId`, and `status = CAPTURED`.
3. THE context SHALL assign each `Captured Trade` a `tradeId` of the form `FX-` followed by a zero-padded six-digit sequence, unique across all captured trades and never reused.
4. THE context SHALL persist the `Captured Trade` and publish `TradeCaptured` as a single `AtomicPublish` (inherited GP-Rq-7), so that no captured trade exists without its event and no event fires without the captured trade.
5. WHEN a `Trade Request` fails one or more capture validation rules, THE context SHALL reject it without persisting a trade or publishing an event (error shape inherited GP-Rq-3).

---

### Requirement 2: Trade-Request Capture Validation

**User Story:** As trade operations, I want every submission checked against the trade-capture business rules before it is asserted, so that only well-formed, economically sensible trades enter the platform.

#### Acceptance Criteria

1. THE context SHALL reject a `Trade Request` WHEN `currencyPair` is absent or its `pairCode` does not match `^[A-Z]{3}/[A-Z]{3}$` (a three-letter base code, `/`, a three-letter quote code).
2. THE context SHALL reject a `Trade Request` WHEN `notionalAmount` is absent, zero, or negative (notional SHALL be strictly positive).
3. THE context SHALL reject a `Trade Request` WHEN `notionalCurrency` is absent, blank, or not exactly three uppercase letters in ISO-4217 currency-code form.
4. THE context SHALL reject a `Trade Request` WHEN `direction` is absent or not one of the `Trade Direction` values (`BUY`, `SELL`).
5. THE context SHALL reject a `Trade Request` WHEN `tradeDate` is absent or falls more than five `Business Day`s before the current date at submission time.
6. THE context SHALL reject a `Trade Request` WHEN `valueDate` is absent or is not strictly after `tradeDate`.
7. THE context SHALL reject a `Trade Request` WHEN `counterpartyId` is absent or blank.
8. THE context SHALL reject a `Trade Request` WHEN `tradingBookId` is absent or blank.
9. THE context SHALL reject a `Trade Request` WHEN `regionCode` is absent or not one of the `Region Code` values (`APAC`, `EMEA`, `AMERICAS`, `GLOBAL`).
10. WHEN a `Trade Request` violates one or more rules, THE context SHALL report one field-level error per failed field (envelope shape inherited GP-Rq-3), evaluating all rules so every violation is surfaced together.
11. WHEN a `Trade Request` satisfies every rule, THE context SHALL proceed to capture without mutating any supplied field value (structural validation via `BEAN_VALIDATION`; cross-field and calendar rules evaluated deterministically per inherited GP-Rq-13).

---

### Requirement 3: Exactly-Once Trade Capture

**User Story:** As a submitting system, I want to retry a submission safely after a network failure, so that one trade is captured exactly once no matter how many times I resend it.

#### Acceptance Criteria

1. WHEN the same submission intent is replayed (same `IdempotencyKey`, inherited GP-Rq-5), THE context SHALL return the original `Captured Trade`'s confirmation and SHALL NOT assert a second trade or publish a second `TradeCaptured` event.
2. THE context SHALL record its `Exactly-Once Capture` marker in the `CACHE` role only after the `Captured Trade` is durably persisted and its `TradeCaptured` event committed, so a submission that fails before capture completes leaves no marker and remains retryable (inherited GP-Rq-5).
3. THE context SHALL treat exactly-once capture as a **domain guarantee that one trade is asserted per intent** — the replay outcome references the same `tradeId` as the original capture, never a newly minted one.

---

### Requirement 4: TradeCaptured Domain Event

**User Story:** As a downstream context, I want a `TradeCaptured` event whenever a trade is first asserted, so that the lifecycle can proceed without polling the store of record.

#### Acceptance Criteria

1. WHEN a `Captured Trade` is persisted, THE context SHALL publish exactly one `TradeCaptured` event of shared-kernel type `TRADE_CAPTURED`, carrying the assigned `tradeId` and the captured trade fields (base envelope fields — `eventId`, `CorrelationId`, `sourceService`, `occurredAt` — are inherited GP-Rq-7).
2. THE context SHALL be the sole originator of `TradeCaptured`; it SHALL NOT publish any later-lifecycle `TradeEventType`.
3. THE `TradeCaptured` payload SHALL faithfully reflect the persisted `Captured Trade` (currency pair, notional amount and currency, direction, trade date, value date, counterparty, trading book, region, and `status = CAPTURED`), serialized per the `SERIALIZATION` role.

---

### Requirement 5: Domain Acceptance Scenarios

**User Story:** As a QA engineer, I want the capture-specific behaviors pinned by tests, so that the trade-capture domain rules never regress. (General testing standards are inherited from GP-Rq-12; these are the domain scenarios that must be covered.)

#### Acceptance Criteria

1. THE context SHALL be covered by a test asserting that a fully valid `Trade Request` yields a `Captured Trade` at status `CAPTURED`, a returned `tradeId` matching `^FX-\d{6}$`, and a published `TradeCaptured` event whose `tradeId` matches.
2. THE context SHALL be covered by tests asserting that each capture validation rule in Requirement 2 — malformed `pairCode`, non-positive notional, non-ISO-4217 currency, invalid direction, `tradeDate` more than five `Business Day`s in the past, `valueDate` not strictly after `tradeDate`, missing counterparty/book/region — independently rejects the request with a field-level error and asserts no trade is persisted and no event is published.
3. THE context SHALL be covered by a test asserting that re-submitting the same intent returns the original `Captured Trade` (same `tradeId`) and produces no second persisted trade and no second `TradeCaptured` event (domain view of inherited GP-Rq-5).
4. THE context SHALL be covered by an end-to-end test (per `INTEGRATION_TEST_HARNESS`) that submits a valid trade and asserts all of: the successful-creation confirmation, a `Captured Trade` present in the `RELATIONAL_STORE` at status `CAPTURED`, and a matching `TradeCaptured` event on the `EVENT_STREAM` — using only `FX-` prefixed identifiers and fictional counterparty/book names.
