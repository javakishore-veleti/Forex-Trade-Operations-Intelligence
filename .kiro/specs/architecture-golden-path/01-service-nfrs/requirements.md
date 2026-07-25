# Requirements Document — Service Non-Functional Requirements (Golden Path)

> **Technology-agnostic spec.** References **Technology Roles** from `01-initial-setup/01-technology-stack`. Contains no product names or versions.

## Introduction

This feature defines the **architecture golden path**: the cross-cutting, non-functional requirements that **every microservice** in `Middleware/` inherits by default. It exists so that individual service specs contain **only business/domain requirements** and do not repeat correlation-ID handling, health probes, security placeholders, error envelopes, event atomicity, observability, resilience, configuration, or testing standards.

**Inheritance rule:** every `Middleware/` service spec SHALL state "Inherits `architecture-golden-path/01-service-nfrs`" and SHALL NOT restate any requirement defined here. A service spec restates a golden-path requirement only to **narrow or override** it, and only with an explicit "overrides GP-Rq-N" note. Where a golden-path requirement uses "SHALL apply to every service," it is binding on each service without repetition.

This is one of two shared single-sources-of-truth: the Technology Stack owns *what technology*, and this golden path owns *how every service behaves*. All example identifiers use the synthetic `FX-` prefix. All organization names are fictional.

---

## Glossary

- **Service**: Any microservice module under `Middleware/` (a bounded context in the domain model).
- **GoldenPath**: The set of non-functional requirements defined by this spec, inherited by every `Service`.
- **CorrelationId**: A UUID that ties every log line, published event, and response for a single logical request together end-to-end.
- **ErrorEnvelope**: The standard structured error body returned by every `Service` on failure.
- **ReadinessProbe**: The health indicator reporting whether a `Service` is ready to receive traffic, based on its dependencies.
- **LivenessProbe**: The health indicator reporting whether a `Service`'s application context is running.
- **IdempotencyKey**: A caller- or event-supplied key that makes a repeated operation safe to execute exactly once.
- **AtomicPublish**: The guarantee that a state change and its resulting domain event are committed together (e.g. transactional outbox or transactional producer), per the `STREAM_PROCESSING`/`EVENT_STREAM` roles.
- **SecurityPlaceholder**: The early-phase permit-all security configuration that marks the future authentication integration point.
- **SyntheticData**: Test/example data using only `FX-` prefixed identifiers and fictional names.

---

## Requirements

### Requirement 1 (GP-Rq-1): API Conventions and Versioning

**User Story:** As an API consumer, I want every service to expose consistently shaped, versioned HTTP APIs, so that I can integrate against a predictable contract.

#### Acceptance Criteria

1. EVERY `Service` that exposes HTTP endpoints SHALL root them under a versioned base path `/api/v{n}` (initially `/api/v1`).
2. EVERY `Service` SHALL use HTTP status semantics consistently: `200` for successful reads, `201` for successful creation, `400` for validation failure, `404` for unknown resource, `409` for conflict/idempotency/optimistic-lock collision, `503` for not-ready, `500` for unhandled error.
3. EVERY `Service` SHALL accept and produce JSON serialized per the `SERIALIZATION` role (ISO-8601 temporals, numeric monetary values).
4. EVERY read endpoint SHALL be side-effect free and SHALL NOT trigger a state change or event publication.

---

### Requirement 2 (GP-Rq-2): Correlation ID Propagation

**User Story:** As a platform operator, I want a single correlation ID to flow through every log line, event, and response for a request, so that I can trace it end-to-end across services.

#### Acceptance Criteria

1. WHEN a request or consumed event carries a non-blank `X-Correlation-Id` (or event `correlationId`), THE `Service` SHALL adopt it as the `CorrelationId` for that unit of work.
2. WHEN no correlation identifier is supplied, THE `Service` SHALL generate a new UUID `CorrelationId`.
3. EVERY `Service` SHALL place the `CorrelationId` in the logging context (per the `OBSERVABILITY_LOGGING` role) before emitting any log line for the unit of work, and SHALL clear it afterward.
4. EVERY `Service` SHALL set the `CorrelationId` on every domain event it publishes and on the `X-Correlation-Id` header of every HTTP response.

---

### Requirement 3 (GP-Rq-3): Structured Error Responses

**User Story:** As an API consumer, I want machine-readable error responses, so that I can handle failures programmatically without parsing free text.

#### Acceptance Criteria

1. WHEN a request fails validation, THE `Service` SHALL return `400` with an `ErrorEnvelope` containing `status`, `timestamp` (ISO-8601), and `errors` (array of `{field, message}`).
2. WHEN a request conflicts (idempotency replay or optimistic-lock collision), THE `Service` SHALL return `409` with an `ErrorEnvelope` containing `status`, `timestamp`, `message`, and the conflicting key/identifier.
3. WHEN an unhandled error occurs, THE `Service` SHALL return `500` with an `ErrorEnvelope` containing `status`, `timestamp`, and `requestId`, and SHALL NOT include a stack trace or internal exception message in the body.
4. EVERY `Service` SHALL log `400`/`409` at WARN and `500` at ERROR, each with the `CorrelationId` (and `tradeId` when available); the full stack trace SHALL appear only in the log, never the response.

---

### Requirement 4 (GP-Rq-4): Health and Readiness Probes

**User Story:** As an orchestrator, I want standard liveness and readiness probes, so that I can route traffic only to healthy instances.

#### Acceptance Criteria

1. EVERY `Service` SHALL expose a health endpoint providing a `LivenessProbe` and a `ReadinessProbe`.
2. THE `LivenessProbe` SHALL report `UP` when the application context has started successfully.
3. THE `ReadinessProbe` SHALL report `UP` only when all of the `Service`'s declared runtime dependencies (its bound `RELATIONAL_STORE`, `DOCUMENT_STORE`, `CACHE`, `GRAPH_STORE`, `EVENT_STREAM`, and downstream services, as applicable to that service) are reachable; otherwise `DOWN` identifying the failing dependency.
4. THE health endpoint SHALL return `200` when ready and `503` when any readiness dependency is down.
5. WHERE a service can operate in a degraded mode with one non-critical dependency unavailable, THE service spec MAY override this requirement to define degraded readiness (with an explicit "overrides GP-Rq-4" note).

---

### Requirement 5 (GP-Rq-5): Idempotency

**User Story:** As a calling system or event consumer, I want repeated operations to take effect exactly once, so that retries and at-least-once delivery cannot cause duplicate effects.

#### Acceptance Criteria

1. WHERE a `Service` exposes a state-changing HTTP operation, THE operation SHALL accept an `IdempotencyKey` and SHALL return the original result (via `409` or a cached response) when the same key is replayed, without re-executing side effects.
2. WHERE a `Service` consumes domain events, THE `Service` SHALL deduplicate by event identity (`eventId`) so that reprocessing the same event produces no additional state change and no duplicate published event.
3. EVERY `Service` SHALL store idempotency/dedup markers in the `CACHE` or `RELATIONAL_STORE` role with a defined retention.
4. EVERY `Service` SHALL NOT persist an idempotency marker for an operation that failed before completing its side effects.

---

### Requirement 6 (GP-Rq-6): Concurrency and Optimistic Locking

**User Story:** As a data owner, I want concurrent writes to be detected rather than silently lost, so that conflicting updates fail safely.

#### Acceptance Criteria

1. EVERY `Service` writing to the `RELATIONAL_STORE` SHALL use optimistic locking via a `version` field as the platform default (no pessimistic row locks unless a service spec justifies an override).
2. WHEN an optimistic-lock conflict occurs, THE `Service` SHALL return `409` per GP-Rq-3 and SHALL NOT overwrite the conflicting record.

---

### Requirement 7 (GP-Rq-7): Event Publishing Atomicity and Consumption

**User Story:** As the event-driven platform, I want state changes and their events committed together, so that no state exists without its event and no event fires without its state.

#### Acceptance Criteria

1. WHEN a `Service` both persists a state change and publishes a resulting domain event, THE `Service` SHALL do so as an `AtomicPublish` (transactional outbox or transactional producer) so the two commit together.
2. WHEN the event publish fails after a state write, THE `Service` SHALL roll back or compensate so that no orphan state remains.
3. EVERY event consumer SHALL acknowledge the `EVENT_STREAM` offset only after the unit of work (including dedup marker) is durably committed.
4. EVERY published domain event SHALL be a `shared-domain-contracts` event type, carry a unique `eventId`, a `CorrelationId`, a `sourceService`, and an `occurredAt` timestamp.

---

### Requirement 8 (GP-Rq-8): Observability

**User Story:** As an operator, I want every service instrumented consistently, so that traces, metrics, and logs correlate across the platform.

#### Acceptance Criteria

1. EVERY `Service` SHALL emit structured logs including `CorrelationId` and, where applicable, `tradeId`, per the `OBSERVABILITY_LOGGING` role.
2. EVERY `Service` SHALL propagate distributed trace context (per `OBSERVABILITY_TRACING`) across inbound HTTP, outbound HTTP, and `EVENT_STREAM` boundaries.
3. EVERY `Service` SHALL expose runtime metrics (per `OBSERVABILITY_METRICS`) covering request rate, error rate, and latency, plus service-specific business metrics declared in that service's spec.

---

### Requirement 9 (GP-Rq-9): Security Configuration Placeholder

**User Story:** As a security engineer, I want a clearly marked authentication integration point in every service, so that authentication can be wired in a later phase without restructuring.

#### Acceptance Criteria

1. EVERY `Service` SHALL include a `SecurityPlaceholder` configuration that permits all requests in the current phase.
2. THE `SecurityPlaceholder` SHALL contain an explicit, uniformly-worded marker indicating where token-based authentication (per a later phase) will replace permit-all.
3. NO `Service` SHALL reject a request for missing/invalid credentials in the current phase.
4. NO `Service` SHALL store credential values, secrets, or tokens in source or configuration committed to the repository.

---

### Requirement 10 (GP-Rq-10): Resilience of Downstream Calls

**User Story:** As a platform operator, I want inter-service calls to fail fast and degrade gracefully, so that one slow dependency does not cascade.

#### Acceptance Criteria

1. EVERY outbound call a `Service` makes to another service SHALL apply a bounded timeout.
2. WHERE a `Service` retries a failed downstream call, THE retry SHALL use bounded, backoff-based attempts and SHALL be safe under the idempotency guarantees of GP-Rq-5.
3. WHERE a downstream dependency is persistently failing, THE `Service` SHALL apply a circuit-breaker or equivalent so it fails fast rather than blocking indefinitely.

---

### Requirement 11 (GP-Rq-11): Configuration and Profiles

**User Story:** As a deployer, I want configuration externalized and environment-selectable, so that the same artifact runs locally and in each cloud without code changes.

#### Acceptance Criteria

1. EVERY `Service` SHALL externalize all environment-specific configuration (endpoints, credentials references, tuning) outside the built artifact.
2. EVERY `Service` SHALL support environment profiles that resolve the correct `CloudTargetBinding` for each infrastructure role (local, AWS, Azure) without code changes.
3. NO `Service` SHALL hard-code an endpoint, hostname, or secret; secrets are supplied via the environment/secret mechanism only.

---

### Requirement 12 (GP-Rq-12): Testing Standards

**User Story:** As a QA engineer, I want a uniform testing bar for every service, so that quality is consistent and regressions are caught before release.

#### Acceptance Criteria

1. EVERY `Service` SHALL provide unit tests (per `UNIT_TEST_FRAMEWORK`) covering its business rules and, where it exposes HTTP endpoints, web-layer tests (per `WEB_LAYER_TEST`) covering success and each error path of GP-Rq-3.
2. EVERY `Service` SHALL provide at least one integration test (per `INTEGRATION_TEST_HARNESS`, provisioning real dependency instances) covering its primary end-to-end flow.
3. WHERE a `Service` has non-trivial validation or transformation logic, THE `Service` SHALL provide property-based tests (per `PROPERTY_TEST`).
4. EVERY test SHALL use only `SyntheticData` (`FX-` prefixed IDs, fictional names).
5. WHEN the module's test command is executed, THE `Service` SHALL complete all tests with zero failures and zero errors.

---

### Requirement 13 (GP-Rq-13): Determinism and LLM Boundary

**User Story:** As a risk and audit stakeholder, I want business/transactional services to be fully deterministic, so that official outcomes are reproducible and never produced by a model.

#### Acceptance Criteria

1. NO `Service` SHALL invoke a large language model or any non-deterministic external inference in the computation of any official figure, state transition, rule evaluation, or permitted-action decision.
2. EVERY `Service` SHALL produce identical outputs for identical inputs and the same reference-data/rule version (determinism guarantee), except for intentionally time- or sequence-derived fields.
3. EVERY `Service` SHALL keep all business/transactional logic in `SERVICE_LANGUAGE`; the `SIDECAR_LANGUAGE` and `AGENT_PLATFORM` are never used for business logic (they surround services, per the platform architecture).

---

### Requirement 14 (GP-Rq-14): Synthetic Data and Public Safeguard

**User Story:** As the maintainer of a public reference implementation, I want every service to use only synthetic data, so that no real financial data is ever committed.

#### Acceptance Criteria

1. EVERY `Service` SHALL use only `SyntheticData` in code comments, configuration examples, README content, and test fixtures.
2. NO `Service` SHALL introduce real counterparty/account names, production endpoints, secrets, proprietary topic names, schemas, or rule thresholds.
3. EVERY trade identifier used in any example SHALL match the pattern `FX-` followed by digits.
