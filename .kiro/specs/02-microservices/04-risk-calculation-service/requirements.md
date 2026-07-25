# Requirements Document — Risk Calculation (Bounded Context)

> **Inherits `architecture-golden-path/01-service-nfrs`** (all cross-cutting NFRs) and references **Technology Roles** from `01-initial-setup/01-technology-stack`. This spec contains **only business/domain requirements** plus any service-specific narrowing of a golden-path requirement. No product names, versions, or repeated NFRs.

## Introduction

The **Risk Calculation** bounded context is the platform's **deterministic risk authority**. It is realized by the `risk-calculation-service` microservice (`Middleware/risk-calculation-service/`). The context's single responsibility is to **compute official risk figures**: given a request to price a trade's risk, it evaluates currency-pair business rules through the `RULES_ENGINE`, derives a risk amount by exact arithmetic, decomposes that amount into contributing factors, maintains regional / trading-book / global aggregations, checks configured limits, and announces completion. Every figure it produces is reproducible and explainable, and none of it is ever produced by a model — the determinism/LLM boundary is inherited from GP-Rq-13.

It does **not** own trade lifecycle state, decide or execute limit-breach responses, move money, or author its own rules — those are other bounded contexts. It records facts (a completed calculation, a breach) and lets downstream contexts act on them.

Cross-cutting concerns (correlation IDs, health/readiness probes, error envelopes, security placeholder, event-publish atomicity, idempotent consumption, optimistic locking, observability, configuration, testing standards, and the generic determinism/LLM boundary) are **inherited from the golden path** and are not restated here. All identifiers in examples and tests use the synthetic `FX-` prefix (e.g. `FX-000001`); all rule identifiers (e.g. `FX-REGION-APAC-042`), thresholds, and names are fictional.

---

## Bounded Context and Ubiquitous Language

- **Risk Calculation**: This bounded context.
- **Risk Calculation Request**: The domain input (from the shared kernel) describing the trade to be priced: `tradeId`, notional, `CurrencyPair`, `regionCode`, `tradingBookId`, and market-data reference.
- **Risk Result**: The authoritative output of a single calculation, identified by `calculationId` and bound to a `tradeId`.
- **Currency Pair**: The base/quote currencies of a trade, from the shared kernel.
- **Risk Amount**: The official risk figure, held as **fixed-scale decimal arithmetic (no floating point)** with a defined scale and rounding mode.
- **Risk Level**: The classification band (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`) derived deterministically from the `Risk Amount`.
- **Rule Version**: The version identifier of the deployed rule package used for a calculation (e.g. `RULES-7.14`).
- **Rules Fired**: The ordered list of rule identifiers that matched during a `RULES_ENGINE` evaluation (e.g. `FX-REGION-APAC-042`).
- **Fallback Rule**: The defined default rule applied when no specific currency-pair rule matches, whose firing is recorded so uncovered pairs are observable.
- **Contributing Factor**: A named component of a `Risk Amount` (`factorName`, contribution amount, currency); the factors sum to the `Risk Amount` within the rounding tolerance.
- **Risk Aggregation**: A running total of risk amounts grouped by `regionCode`, by `tradingBookId`, or globally.
- **EOD Risk Total**: The finalized `Risk Aggregation` snapshot taken at end-of-day for a region or globally.
- **Limit**: A configured maximum permitted risk amount scoped to a region, trading book, or counterparty.
- **Limit-Breach Fact**: A recorded observation that a `Risk Result` or `Risk Aggregation` exceeded a `Limit`; a fact only, never an action.

**Consumed domain events** (from the shared kernel `EVENT_STREAM`): `RISK_CALCULATION_REQUESTED`; and the trade `CANCELLED` / `AMENDED` signals that require aggregation adjustment.
**Produced domain events** (to the shared kernel `EVENT_STREAM`): `RISK_CALCULATION_COMPLETED`; and a trade-failed signal when reference data cannot be resolved.
**Persistence roles used** (declares this service's readiness dependencies for golden-path GP-Rq-4): `RELATIONAL_STORE` (risk results, aggregations, limits, EOD snapshots), `EVENT_STREAM` (event consumption and publication), `RULES_ENGINE` (loaded, versioned rule package), `CACHE` (idempotent consumption markers).

---

## Requirements

### Requirement 1: Risk Calculation Request Consumption

**User Story:** As the trade lifecycle pipeline, I want risk computed automatically when a calculation is requested, so that a trade can progress without manual intervention.

#### Acceptance Criteria

1. WHEN a `RISK_CALCULATION_REQUESTED` event is consumed from the `EVENT_STREAM`, THE Risk Calculation context SHALL construct a `Risk Calculation Request` from the event payload and initiate a calculation.
2. THE context SHALL also expose an on-demand operation that accepts a `Risk Calculation Request` and returns the resulting `Risk Result`, for recalculation (side-effect boundaries per inherited GP-Rq-1).
3. WHEN a request references a `tradeId` whose trade reference data cannot be resolved, THE context SHALL publish a trade-failed domain event carrying a reason code and SHALL NOT produce a `Risk Result`.

---

### Requirement 2: Deterministic Currency-Pair Risk Computation

**User Story:** As a risk analyst, I want risk amounts computed by deterministic code from trade characteristics and market factors, so that the same inputs always yield the same official figure.

#### Acceptance Criteria

1. THE context SHALL compute the `Risk Amount` using only deterministic arithmetic over trade notional, currency-pair factors, `regionCode`, `tradingBookId`, and a market-data snapshot.
2. THE context SHALL perform all monetary arithmetic using **fixed-scale decimal arithmetic (no floating point)** with a defined scale and rounding mode; it SHALL NOT use floating-point representations for any risk figure.
3. THE context SHALL produce a `Risk Result` containing at least `tradeId`, `calculationId`, `Risk Amount`, risk currency, `regionCode`, `tradingBookId`, calculated-at instant, `Rule Version`, and `Risk Level`.
4. THE context SHALL classify `Risk Level` from `Risk Amount` using deterministic, configurable thresholds mapping to `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
5. WHEN a recalculation is requested for a trade with a prior `Risk Result`, THE context SHALL include the previous `Risk Amount` in the result so callers can compute the delta deterministically.

---

### Requirement 3: Rules Engine Evaluation and Explainability

**User Story:** As a rules owner, I want currency-pair rules evaluated by the deterministic rules engine and the fired rules recorded, so that every risk figure is explainable and traceable to specific rule identifiers and a rule version.

#### Acceptance Criteria

1. THE context SHALL evaluate currency-pair risk rules through the `RULES_ENGINE`, loaded from a versioned rule package via configuration.
2. THE context SHALL record the ordered list of `Rules Fired` identifiers for each calculation and expose them on the `Risk Result`.
3. THE context SHALL stamp each `Risk Result` with the `Rule Version` of the rule package used for that calculation.
4. WHEN no specific currency-pair rule matches a trade, THE context SHALL apply the defined `Fallback Rule` and record that the fallback fired, so uncovered-pair usage is observable (input to the later rule-coverage agent context).
5. THE context SHALL NOT permit any agent or model to author, modify, or activate rules; rule packages are deployed through controlled configuration only (per inherited GP-Rq-13).

---

### Requirement 4: Contributing Factors

**User Story:** As a risk explainability consumer, I want each risk result broken into named contributing factors, so that later contexts can explain *why* a figure has its value without recomputing it.

#### Acceptance Criteria

1. THE context SHALL produce a list of `Contributing Factor` entries for each `Risk Result`, each with `factorName`, a contribution amount (**fixed-scale decimal arithmetic**), and a currency.
2. THE sum of all `Contributing Factor` contribution amounts SHALL equal the `Risk Result`'s `Risk Amount` within the defined rounding tolerance.
3. THE context SHALL include, at minimum, factor entries for currency-pair volatility, notional exposure, and any region- or book-specific adjustment applied by a fired rule.

---

### Requirement 5: Regional, Book, and Global Aggregation

**User Story:** As an EOD and exposure consumer, I want risk aggregated by region, trading book, and globally, so that totals and exposure views are available without scanning individual trades.

#### Acceptance Criteria

1. THE context SHALL maintain running `Risk Aggregation` totals grouped by `regionCode`, by `tradingBookId`, and globally.
2. WHEN a new `Risk Result` is produced, THE context SHALL update the affected regional, book, and global aggregations together with the persistence of the `Risk Result` (atomicity per inherited GP-Rq-7).
3. WHEN a trade is `CANCELLED` or superseded by an amendment recalculation, THE context SHALL adjust the affected aggregations so a superseded result is never double-counted.
4. THE context SHALL expose a query returning the current aggregation total for a requested scope (`region`, `book`, or `global`) and identifier.
5. THE `GLOBAL` aggregation SHALL represent the consolidated total across the platform's regional aggregations.

---

### Requirement 6: Limit Checking

**User Story:** As a risk controller, I want calculated risk checked against configured limits, so that breaches are detected deterministically and surfaced for downstream action.

#### Acceptance Criteria

1. THE context SHALL evaluate each `Risk Result` and each affected `Risk Aggregation` against configured `Limit` values scoped to region, trading book, and counterparty.
2. WHEN a calculated risk or aggregation exceeds a configured `Limit`, THE context SHALL record a `Limit-Breach Fact` capturing the breached scope, the limit value, and the observed value.
3. THE context SHALL expose the recorded `Limit-Breach Fact`s for query and SHALL include any breach relevant to a calculation in that calculation's result.
4. THE context SHALL NOT itself block, cancel, or reroute a trade on a breach; it records the fact only (action coordination belongs to a later agent context).

---

### Requirement 7: EOD Risk Totals and Completion Event

**User Story:** As the EOD processing context, I want finalized end-of-day totals per region and globally, plus a completion event per trade, so that end-of-day readiness and lifecycle progression can proceed.

#### Acceptance Criteria

1. THE context SHALL expose an operation that finalizes and stores an `EOD Risk Total` snapshot for a given region (or globally) at the moment of invocation.
2. THE `EOD Risk Total` snapshot SHALL capture the aggregation total, per-book breakdown, contributing trade count, and the `Rule Version` in effect at snapshot time.
3. THE context SHALL treat EOD snapshotting as idempotent per (scope, business date): a repeated snapshot for the same scope and business date SHALL overwrite deterministically rather than accumulate.
4. WHEN a per-trade risk calculation completes successfully, THE context SHALL publish a `RISK_CALCULATION_COMPLETED` domain event referencing the `calculationId`, together with persistence of the `Risk Result` (atomicity per inherited GP-Rq-7; event envelope fields per GP-Rq-7).

---

### Requirement 8: Domain Acceptance Scenarios

**User Story:** As a QA engineer, I want the risk-specific behaviors pinned by tests, so that the domain invariants never regress. (General testing standards are inherited from GP-Rq-12; these are the domain scenarios that must be covered.)

#### Acceptance Criteria

1. THE context SHALL be covered by a test asserting the domain determinism guarantee: identical inputs and the same `Rule Version` produce an identical `Risk Amount` and `Risk Level` (domain view of inherited GP-Rq-13).
2. THE context SHALL be covered by a test asserting that the `Contributing Factor` amounts sum to the `Risk Amount` within the defined rounding tolerance (Requirement 4).
3. THE context SHALL be covered by a test asserting that a currency pair with no specific rule triggers the `Fallback Rule` and records fallback firing (Requirement 3).
4. THE context SHALL be covered by an end-to-end test (per the `INTEGRATION_TEST_HARNESS`) that consumes a `RISK_CALCULATION_REQUESTED` event and asserts a `Risk Result` is persisted, the affected aggregations are updated, and a `RISK_CALCULATION_COMPLETED` event is published.
5. THE context SHALL be covered by a test asserting that a `CANCELLED` or amendment-superseded trade adjusts the affected aggregations without double-counting (Requirement 5).
