# Requirements Document — Shared Kernel (Domain Contracts)

> **Technology-agnostic spec.** References **Technology Roles** from `01-initial-setup/01-technology-stack` and the applicable **golden-path** items from `architecture-golden-path/01-service-nfrs`. This is the DDD **Shared Kernel** depended on by every bounded context; it contains no product names or versions.

## Introduction

The `shared-domain-contracts` module is the platform's **Domain-Driven Design Shared Kernel**: the single, authoritative definition of the domain types, enums, and value objects shared across **every** bounded context in the `Middleware/` layer of the Forex Trade Operations Intelligence platform. Its purpose is to guarantee that all contexts speak one identical, type-safe ubiquitous language with no divergent copies and no schema drift.

**This is a shared library, not a runtime microservice.** It is built by the `SERVICE_BUILD_TOOL` as a shared library and is depended on at **compile scope** by every service. It carries **no runtime framework**: no HTTP endpoints, no event-stream producers or consumers, no persistence layer, no health probes, no security configuration, and no agent-tool wiring. Consequently it does **not** inherit the service runtime non-functional requirements of the golden path — there is no bounded-context/aggregate/readiness framing here. It **does** follow technology-agnostic authoring, the golden-path **testing standards** (GP-Rq-12) and **synthetic-data safeguard** (GP-Rq-14), and it **provides** the `SERIALIZATION` and `BEAN_VALIDATION` behaviors as reusable capabilities that consuming services activate at their own boundaries.

All identifiers in examples, tests, and documentation use the synthetic `FX-` prefix (e.g. `FX-000001`). All organization, service, and region names are fictional.

---

## Glossary

- **SharedKernel**: The shared library module defined by this spec (`Middleware/shared-domain-contracts/`) — the single source of shared domain types.
- **DomainType**: An immutable domain type (value object) provided by the `SharedKernel`.
- **Trade**: A single foreign-exchange trade transaction captured and processed by the platform.
- **TradeRecord**: The `DomainType` representing a `Trade`.
- **TradeStatus**: Enum representing the lifecycle state of a `Trade`.
- **TradeDirection**: Enum representing whether a trade is a buy or sell.
- **CurrencyPair**: `DomainType` identifying the two ISO-4217 currencies involved in a trade and their combined pair code.
- **TradeEvent**: `DomainType` representing a domain event that occurred during trade processing.
- **TradeEventType**: Enum enumerating all possible `TradeEvent` categories.
- **RiskResult**: `DomainType` holding the output of a risk calculation for a single trade.
- **RiskLevel**: Enum classifying the magnitude of calculated risk.
- **ContributingFactor**: `DomainType` capturing a named component of a risk calculation.
- **RiskCalculationRequest**: `DomainType` representing a request to compute risk for a trade.
- **Region**: `DomainType` describing a geographic or operational region supported by the platform.
- **RegionCode**: Enum identifying the four supported operational regions.
- **TradingBook**: `DomainType` representing a trading book to which trades are assigned.
- **BookType**: Enum classifying the instrument type covered by a `TradingBook`.
- **Counterparty**: `DomainType` representing an external or internal party to a trade.
- **CounterpartyType**: Enum classifying the nature of a `Counterparty`.
- **Money**: Value-object `DomainType` pairing a monetary amount with its ISO-4217 currency code.
- **AuditInfo**: Value-object `DomainType` capturing creation and modification audit metadata.
- **PageRequest**: Value-object `DomainType` encapsulating pagination and sort parameters for list queries.
- **PageResponse\<T\>**: Generic value-object `DomainType` wrapping a paginated list response.
- **ISO-4217**: The international standard for currency codes (e.g. `USD`, `INR`, `EUR`).
- **SyntheticData**: Test/example data using only `FX-` prefixed identifiers and fictional names (per GP-Rq-14).

---

## Requirements

### Requirement 1: Shared Kernel Packaging and Boundaries

**User Story:** As a Middleware service developer, I want the shared domain types delivered as a single compile-scope library with no runtime framework, so that every bounded context receives one identical set of domain types without pulling in any service runtime, messaging, or persistence machinery.

#### Acceptance Criteria

1. THE `SharedKernel` SHALL be built by the `SERVICE_BUILD_TOOL` as a shared library that every service depends on at **compile scope** only.
2. THE `SharedKernel` SHALL target the platform `SERVICE_LANGUAGE` and SHALL be structured as a library artifact (not a runnable application), producing no executable/repackaged runtime output.
3. THE `SharedKernel` SHALL declare itself under the shared build descriptor so that individual modules do not re-declare shared dependency versions.
4. THE `SharedKernel` SHALL depend, at compile scope, only on the `SERIALIZATION` role and the `BEAN_VALIDATION` role sufficient to declare validation rules and enable serialization of its `DomainType`s, including timestamp, date, and time-zone value types.
5. THE `SharedKernel` SHALL NOT depend on any service runtime framework, `EVENT_STREAM` client, `STREAM_PROCESSING` library, persistence/data-store layer, or agent-tool mechanism.
6. THE `SharedKernel` SHALL declare, at test scope only, the `UNIT_TEST_FRAMEWORK` and a runtime implementation of the `BEAN_VALIDATION` role sufficient to execute its tests.

---

### Requirement 2: Trade Domain Model

**User Story:** As a trade processing service developer, I want immutable domain types and enums that represent a foreign-exchange trade and its lifecycle states, so that all contexts share an identical, type-safe trade representation without drift.

#### Acceptance Criteria

1. THE `SharedKernel` SHALL provide a `TradeRecord` `DomainType` with the following components: `tradeId` (text), `correlationId` (text), `currencyPair` (`CurrencyPair`), `notionalAmount` (fixed-scale decimal amount), `notionalCurrency` (text), `direction` (`TradeDirection`), `tradeDate` (date value), `valueDate` (date value), `counterpartyId` (text), `tradingBookId` (text), `regionCode` (`RegionCode`), `status` (`TradeStatus`), `createdAt` (timestamp value), `updatedAt` (timestamp value), and `version` (whole-number).
2. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `tradeId`, `correlationId`, `notionalCurrency`, `counterpartyId`, and `tradingBookId` components of `TradeRecord` are required and non-blank.
3. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `currencyPair`, `notionalAmount`, `direction`, `tradeDate`, `valueDate`, `regionCode`, `status`, `createdAt`, and `updatedAt` components of `TradeRecord` are required (non-null).
4. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `notionalAmount` component of `TradeRecord` is strictly positive (zero and negative rejected).
5. THE `SharedKernel` SHALL provide a `TradeStatus` enum with constants: `CAPTURED`, `VALIDATED`, `ENRICHED`, `RISK_CALCULATED`, `BOOKED`, `ALLOCATED`, `CONFIRMED`, `SETTLED`, `CANCELLED`, `AMENDED`, `FAILED`.
6. THE `SharedKernel` SHALL provide a `TradeDirection` enum with constants: `BUY`, `SELL`.
7. THE `SharedKernel` SHALL provide a `CurrencyPair` `DomainType` with components: `baseCurrency` (text, ISO-4217), `quoteCurrency` (text, ISO-4217), and `pairCode` (text, e.g. `"USD/INR"`).
8. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `baseCurrency`, `quoteCurrency`, and `pairCode` components of `CurrencyPair` are required and non-blank.
9. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `baseCurrency` and `quoteCurrency` components of `CurrencyPair` are exactly three characters, enforcing ISO-4217 currency codes.
10. THE `version` component of `TradeRecord` SHALL be a non-nullable whole-number to support optimistic locking by downstream persistence services.

---

### Requirement 3: Trade Event Domain Model

**User Story:** As an event-processing service developer, I want immutable domain types and enums that represent domain events emitted during trade processing, so that all consumers share an identical, schema-stable event contract.

#### Acceptance Criteria

1. THE `SharedKernel` SHALL provide a `TradeEvent` `DomainType` with the following components: `eventId` (text), `tradeId` (text), `correlationId` (text), `eventType` (`TradeEventType`), `occurredAt` (timestamp value), `sequenceNumber` (whole-number), `sourceService` (text), and `payload` (key-value map of text to arbitrary value).
2. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `eventId`, `tradeId`, `correlationId`, and `sourceService` components of `TradeEvent` are required and non-blank.
3. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `eventType`, `occurredAt`, and `payload` components of `TradeEvent` are required (non-null).
4. THE `SharedKernel` SHALL provide a `TradeEventType` enum with constants: `TRADE_CAPTURED`, `TRADE_VALIDATED`, `TRADE_ENRICHED`, `RISK_CALCULATION_REQUESTED`, `RISK_CALCULATION_COMPLETED`, `TRADE_BOOKED`, `TRADE_ALLOCATED`, `TRADE_CONFIRMED`, `TRADE_SETTLED`, `TRADE_CANCELLED`, `TRADE_AMENDED`, `TRADE_FAILED`, `EVENT_REPLAYED`, `PROCESSING_PAUSED`, `PROCESSING_RESUMED`.
5. THE `sequenceNumber` component of `TradeEvent` SHALL be a non-nullable whole-number to support ordering assertions by event-integrity consumers.

---

### Requirement 4: Risk Domain Model

**User Story:** As a risk calculation service developer, I want immutable domain types and enums for risk results, risk levels, contributing factors, and calculation requests, so that risk outputs and inputs are uniformly typed across all contexts.

#### Acceptance Criteria

1. THE `SharedKernel` SHALL provide a `RiskResult` `DomainType` with the following components: `tradeId` (text), `calculationId` (text), `riskAmount` (fixed-scale decimal amount), `riskCurrency` (text), `regionCode` (`RegionCode`), `tradingBookId` (text), `calculatedAt` (timestamp value), `ruleVersion` (text), and `riskLevel` (`RiskLevel`).
2. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `tradeId`, `calculationId`, `riskCurrency`, `tradingBookId`, and `ruleVersion` components of `RiskResult` are required and non-blank.
3. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `riskAmount`, `regionCode`, `calculatedAt`, and `riskLevel` components of `RiskResult` are required (non-null).
4. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `riskAmount` component of `RiskResult` is strictly positive.
5. THE `SharedKernel` SHALL provide a `RiskLevel` enum with constants: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
6. THE `SharedKernel` SHALL provide a `ContributingFactor` `DomainType` with components: `factorName` (text), `contributionAmount` (fixed-scale decimal amount), and `currency` (text).
7. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `factorName` and `currency` components of `ContributingFactor` are required and non-blank.
8. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `contributionAmount` component of `ContributingFactor` is required (non-null) and strictly positive.
9. THE `SharedKernel` SHALL provide a `RiskCalculationRequest` `DomainType` with components: `tradeId` (text), `correlationId` (text), `requestId` (text), `regionCode` (`RegionCode`), `tradingBookId` (text), `requestedAt` (timestamp value), and `priority` (whole-number).
10. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `tradeId`, `correlationId`, `requestId`, and `tradingBookId` components of `RiskCalculationRequest` are required and non-blank.
11. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `regionCode` and `requestedAt` components of `RiskCalculationRequest` are required (non-null).
12. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `priority` component of `RiskCalculationRequest` is at least `1`, so that priority zero is not permitted.

---

### Requirement 5: Reference Data Domain Model

**User Story:** As a reference-data and lifecycle service developer, I want immutable domain types and enums for regions, trading books, and counterparties, so that all contexts use consistent reference-data representations without defining their own divergent copies.

#### Acceptance Criteria

1. THE `SharedKernel` SHALL provide a `Region` `DomainType` with components: `regionCode` (`RegionCode`), `regionName` (text), `timezone` (time-zone value), `baseCurrency` (text), and `isActive` (boolean flag).
2. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `regionCode` and `timezone` components of `Region` are required (non-null).
3. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `regionName` and `baseCurrency` components of `Region` are required and non-blank.
4. THE `SharedKernel` SHALL provide a `RegionCode` enum with constants: `APAC`, `EMEA`, `AMERICAS`, `GLOBAL`.
5. THE `SharedKernel` SHALL provide a `TradingBook` `DomainType` with components: `bookId` (text), `bookName` (text), `regionCode` (`RegionCode`), `traderId` (text), `isActive` (boolean flag), and `bookType` (`BookType`).
6. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `bookId`, `bookName`, and `traderId` components of `TradingBook` are required and non-blank.
7. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `regionCode` and `bookType` components of `TradingBook` are required (non-null).
8. THE `SharedKernel` SHALL provide a `BookType` enum with constants: `SPOT`, `FORWARD`, `SWAP`, `OPTION`.
9. THE `SharedKernel` SHALL provide a `Counterparty` `DomainType` with components: `counterpartyId` (text), `counterpartyName` (text), `counterpartyType` (`CounterpartyType`), `regionCode` (`RegionCode`), `isActive` (boolean flag), and `creditRating` (text).
10. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `counterpartyId`, `counterpartyName`, and `creditRating` components of `Counterparty` are required and non-blank.
11. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `counterpartyType` and `regionCode` components of `Counterparty` are required (non-null).
12. THE `SharedKernel` SHALL provide a `CounterpartyType` enum with constants: `BANK`, `CORPORATE`, `FUND`, `BROKER`, `INTERNAL`.

---

### Requirement 6: Common Value Objects

**User Story:** As a Middleware service developer, I want shared value-object types for money, audit metadata, and pagination, so that all contexts express these cross-cutting concerns with a single type rather than defining duplicates.

#### Acceptance Criteria

1. THE `SharedKernel` SHALL provide a `Money` `DomainType` with components: `amount` (fixed-scale decimal amount) and `currency` (text).
2. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `amount` component of `Money` is required (non-null) and strictly positive.
3. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `currency` component of `Money` is required, non-blank, and exactly three characters, enforcing ISO-4217 currency codes.
4. WHEN a `Money` value is constructed, THE `SharedKernel` SHALL enforce that the `amount` decimal uses a fixed scale of two.
5. THE `SharedKernel` SHALL provide an `AuditInfo` `DomainType` with components: `createdAt` (timestamp value), `createdBy` (text), `updatedAt` (timestamp value), `updatedBy` (text), and `version` (whole-number).
6. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `createdAt` and `updatedAt` components of `AuditInfo` are required (non-null).
7. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `createdBy` and `updatedBy` components of `AuditInfo` are required and non-blank.
8. THE `SharedKernel` SHALL provide a `PageRequest` `DomainType` with components: `page` (whole-number), `size` (whole-number), `sortBy` (text), and `sortDirection` (text).
9. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `page` component of `PageRequest` is at least `0`.
10. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `size` component of `PageRequest` is at least `1` and at most `100`.
11. THE `SharedKernel` SHALL provide a `PageResponse<T>` generic `DomainType` with components: `content` (list of `T`), `page` (whole-number), `size` (whole-number), `totalElements` (whole-number), and `totalPages` (whole-number).
12. THE `SharedKernel` SHALL enforce, via the `BEAN_VALIDATION` role, that the `content` component of `PageResponse<T>` is required (non-null).

---

### Requirement 7: Validation Rules on All Domain Types (BEAN_VALIDATION capability)

**User Story:** As a Middleware service developer, I want every `DomainType` to carry `BEAN_VALIDATION` rules, so that a single validation call at any context boundary reliably rejects structurally invalid objects before they propagate further.

#### Acceptance Criteria

1. THE `SharedKernel` SHALL declare, via the `BEAN_VALIDATION` role, a required-and-non-blank rule on every text component that is required and must be non-empty.
2. THE `SharedKernel` SHALL declare, via the `BEAN_VALIDATION` role, a required (non-null) rule on every object-type component that must be present but whose content is governed separately.
3. THE `SharedKernel` SHALL declare, via the `BEAN_VALIDATION` role, a strictly-positive rule on every decimal-amount component representing a monetary or risk amount, so that zero and negative values are rejected.
4. THE `SharedKernel` SHALL declare, via the `BEAN_VALIDATION` role, an exactly-three-character rule on every ISO-4217 currency-code text component.
5. WHEN the `BEAN_VALIDATION` role validates a `DomainType` instance that has one or more null required fields, THE `SharedKernel` SHALL produce at least one constraint violation for each null required field.
6. WHEN the `BEAN_VALIDATION` role validates a `DomainType` instance whose required fields are all populated with valid values, THE `SharedKernel` SHALL produce an empty set of constraint violations.

---

### Requirement 8: Immutability of All Domain Types

**User Story:** As a Middleware service developer, I want all shared domain types to be immutable value objects, so that no context can accidentally mutate shared state after construction and concurrency safety is structural rather than procedural.

#### Acceptance Criteria

1. THE `SharedKernel` SHALL define all domain model types (`TradeRecord`, `TradeEvent`, `RiskResult`, `ContributingFactor`, `RiskCalculationRequest`, `Region`, `TradingBook`, `Counterparty`, `Money`, `AuditInfo`, `PageRequest`, `PageResponse<T>`) as immutable domain types (value objects).
2. THE `SharedKernel` SHALL NOT expose any mutator (setter) operation on any `DomainType`.
3. THE `SharedKernel` SHALL NOT declare any mutable field on any `DomainType`.
4. WHEN a `DomainType` is constructed with a given set of component values, THE `SharedKernel` SHALL preserve those exact values through all accessor calls without modification.

---

### Requirement 9: Serialization Behavior (SERIALIZATION capability)

**User Story:** As a Middleware service developer, I want all domain types to serialize to and deserialize from JSON via the `SERIALIZATION` role without any custom serializer code, so that HTTP boundaries and event-stream message converters work out of the box without per-type glue code.

#### Acceptance Criteria

1. WHEN a `DomainType` is serialized by the `SERIALIZATION` role, THE `SharedKernel` SHALL produce valid JSON for all component types, including timestamp values, date values, decimal amounts, time-zone values, and key-value maps.
2. WHEN a `DomainType` containing timestamp components is serialized, THE `SharedKernel` SHALL produce ISO-8601 string representations (e.g. `"2025-06-15T09:00:00Z"`) rather than numeric epoch representations.
3. WHEN a `DomainType` containing decimal-amount components is serialized, THE `SharedKernel` SHALL produce JSON number tokens (e.g. `12345.67`) rather than quoted strings.
4. WHEN JSON produced by serializing a `DomainType` is deserialized back into the same type, THE `SharedKernel` SHALL produce a value equal to the original (round-trip property).
5. THE `SharedKernel` SHALL rely on the platform `SERIALIZATION` role configured to render all temporal values as ISO-8601 strings rather than numeric timestamps.
6. WHERE a downstream context requires configuring serialization for these types, THE `SharedKernel` SHALL expose a reusable configuration helper that yields a correctly configured `SERIALIZATION` component for these `DomainType`s.

---

### Requirement 10: Serialization Round-Trip Guarantee

**User Story:** As a Middleware service developer, I want a formal round-trip guarantee for all serialization paths, so that any change to a type's structure or serialization configuration that breaks deserialization is detected by automated tests before reaching downstream consumers.

#### Acceptance Criteria

1. FOR ALL valid instances of `TradeRecord`, `TradeEvent`, `RiskResult`, `RiskCalculationRequest`, `Region`, `TradingBook`, `Counterparty`, `Money`, `AuditInfo`, `PageRequest`, and `PageResponse<TradeRecord>`, serializing to JSON and then deserializing SHALL produce a value equal to the original (round-trip invariant).
2. WHEN a `TradeRecord` JSON payload is deserialized, THE `SERIALIZATION` role SHALL correctly reconstruct the nested `CurrencyPair` value from its JSON object representation.
3. WHEN a `TradeEvent` JSON payload containing a `payload` key-value map is deserialized, THE `SERIALIZATION` role SHALL reconstruct the map with the same keys and value types as the original.
4. WHEN a `PageResponse<TradeRecord>` is serialized and then deserialized against its generic element type, THE `SERIALIZATION` role SHALL produce a list of `TradeRecord` instances equal to the originals.
5. THE `SharedKernel` SHALL provide a shared test utility in the test source root that constructs a correctly configured `SERIALIZATION` component (temporal values as ISO-8601 strings) for use across all test classes.

---

### Requirement 11: Testing Standards (inherits GP-Rq-12; synthetic data per GP-Rq-14)

**User Story:** As a quality-assurance engineer, I want one test suite per `DomainType` that asserts valid construction, `BEAN_VALIDATION` rejection of invalid inputs, and serialization round-trip fidelity, so that regressions in the shared contract are caught before any downstream context is affected.

#### Acceptance Criteria

1. THE `SharedKernel` SHALL provide one `UNIT_TEST_FRAMEWORK` test suite per `DomainType`: `TradeRecordTest`, `CurrencyPairTest`, `TradeEventTest`, `RiskResultTest`, `ContributingFactorTest`, `RiskCalculationRequestTest`, `RegionTest`, `TradingBookTest`, `CounterpartyTest`, `MoneyTest`, `AuditInfoTest`, `PageRequestTest`, and `PageResponseTest`.
2. WHEN a test constructs a `DomainType` with all required fields set to valid `SyntheticData` values (using `FX-` prefixed identifiers where applicable), THE test SHALL assert that all accessors return the exact values supplied at construction.
3. WHEN a test invokes the `BEAN_VALIDATION` role against a `DomainType` in which one required field is null, THE test SHALL assert that the resulting set of constraint violations is non-empty.
4. WHEN a test serializes a `DomainType` via the `SERIALIZATION` role and then deserializes it back, THE test SHALL assert that the deserialized value equals the original.
5. THE `SharedKernel` SHALL use only `SyntheticData` in all test data: trade identifiers matching the pattern `FX-` followed by digits (e.g. `FX-000001`), fictional counterparty names (e.g. `"Aurelia Capital Markets"`), fictional book identifiers (e.g. `"BOOK-APAC-001"`), and fictional service names (e.g. `"trade-ingest-service"`).
6. WHEN the module's test command is executed in the `shared-domain-contracts` module, THE `SharedKernel` SHALL complete all tests with zero failures and zero errors (per GP-Rq-12).

---

### Requirement 12: Synthetic Data and Public Safeguard (inherits GP-Rq-14)

**User Story:** As the maintainer of a public reference implementation, I want the shared kernel to use only synthetic data, so that no real financial data is ever committed in the types every context depends on.

#### Acceptance Criteria

1. THE `SharedKernel` SHALL use only `SyntheticData` in code comments, examples, documentation, and test fixtures.
2. THE `SharedKernel` SHALL NOT introduce real counterparty or account names, production endpoints, secrets, proprietary topic names, schemas, or rule thresholds.
3. EVERY trade identifier used in any example SHALL match the pattern `FX-` followed by digits.
