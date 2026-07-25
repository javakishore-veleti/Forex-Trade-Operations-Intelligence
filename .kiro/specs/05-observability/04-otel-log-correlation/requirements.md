# Requirements Document — Log Correlation

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.

## Introduction

This feature defines the **structured logging pipeline** for the platform:
how logs are formatted, how they are shipped to the `OBSERVABILITY_LOGGING`
role, and how they are correlated with distributed traces and business context.
It ensures that every log line carries `traceId`, `spanId`, `correlationId`,
and `tradeId` (where applicable) so that an operator investigating an incident
can pivot seamlessly from a trace to its logs and vice versa.

This spec has no new business logic. It instruments the logging layer for
services built in phases 02 and 03. All identifiers in examples use the
`FX-` prefix.

---

## Glossary

- **StructuredLog**: A log entry emitted as JSON, not free text, with
  well-known fields that can be indexed and queried.
- **LogPipeline**: The `OBSERVABILITY_LOGGING` pipeline component (log
  shipper/processor) that ingests logs from services, enriches or filters
  them, and forwards them to the log store.
- **LogStore**: The `OBSERVABILITY_LOGGING` search and storage backend.
- **LogDashboard**: A saved query or visualization in the `OBSERVABILITY_LOGGING`
  UI for common log investigation patterns.
- **TraceLogCorrelation**: The ability to navigate from a span in the tracing
  backend to its associated log lines in the `OBSERVABILITY_LOGGING` backend
  using the shared `traceId`.
- **IndexPattern**: The `OBSERVABILITY_LOGGING` UI index pattern that maps to
  the platform's log indices, enabling structured field queries.
- **LogRetention**: The configured duration for which log entries are kept in
  the `LogStore` before being eligible for deletion.

---

## Requirements

### Requirement 1: Structured Log Format

**User Story:** As a developer or operator, I want every service to emit JSON
structured logs with a consistent set of fields so that log queries work the
same way across all services.

#### Acceptance Criteria

1. EVERY `Service_Module` SHALL emit logs in JSON format (per the
   `OBSERVABILITY_LOGGING` role's structured-log configuration), not as
   free-text strings.
2. EVERY `StructuredLog` entry SHALL contain at minimum the following fields:
   `timestamp` (ISO-8601), `level` (e.g. `INFO`, `WARN`, `ERROR`),
   `service` (matching `spring.application.name`), `traceId` (from the active
   `OBSERVABILITY_TRACING` context), `spanId` (from the active
   `OBSERVABILITY_TRACING` context), `correlationId` (from GP-Rq-2 MDC),
   `message` (the log message string), and `logger` (the logger class name).
3. WHEN a log entry is associated with a specific trade, THE `StructuredLog`
   SHALL also include `tradeId` as a top-level field (not embedded in the
   message string) so that it is queryable directly.
4. WHEN a log entry is associated with a specific region, THE `StructuredLog`
   SHALL include `regionCode` as a top-level field.
5. LOG entries SHALL NOT embed stack traces in the `message` field; stack
   traces SHALL be placed in a separate `exception` field (or equivalent
   structured field) so that the `message` field remains queryable without
   noise.
6. NO `StructuredLog` entry SHALL contain real credentials, secrets, or PII;
   only `SyntheticData` (`FX-` prefixed `tradeId`s, fictional names) SHALL
   appear in log fields.

---

### Requirement 2: Log Pipeline Configuration

**User Story:** As a platform engineer, I want the log pipeline configured to
ship logs from all services to the `LogStore` automatically so that no service
requires manual log-forwarding setup.

#### Acceptance Criteria

1. THE `DevOps/Local/` compose stack SHALL include the `LogPipeline` component
   configured to ingest logs from all `Service_Module` containers via the
   `CONTAINER_RUNTIME` logging driver or file-based log shipping.
2. THE `LogPipeline` configuration SHALL parse the JSON `StructuredLog` format
   emitted by services; it SHALL NOT apply additional free-text parsing to
   already-structured fields.
3. THE `LogPipeline` SHALL add an `environment` field (e.g. `local`) to every
   log entry at ingestion time so that logs from different environments can
   be filtered in the same `LogStore`.
4. THE `LogPipeline` configuration SHALL be version-controlled under
   `DevOps/Local/OBSERVABILITY_LOGGING/` and SHALL be loaded automatically
   when the local logging stack starts.
5. THE `LogPipeline` SHALL forward the `EVENT_STREAM` broker logs to the
   `LogStore` in addition to service logs, using the same index pattern.

---

### Requirement 3: Log Store Index Patterns

**User Story:** As a developer or operator querying logs, I want pre-configured
`IndexPattern`s in the `OBSERVABILITY_LOGGING` UI so that I can start querying
without manual setup on a clean environment.

#### Acceptance Criteria

1. THE `OBSERVABILITY_LOGGING` setup SHALL provision at minimum two
   `IndexPattern`s: one for all platform service logs (`fxops-services-*`)
   and one for `EVENT_STREAM` broker logs (`fxops-kafka-*`).
2. THE `IndexPattern` provisioning SHALL be automated as part of the
   `DevOps/Local/OBSERVABILITY_LOGGING/` initialization so that a clean
   local environment has queryable indices without manual steps.
3. THE `fxops-services-*` `IndexPattern` SHALL have `timestamp` set as the
   default time field and SHALL index `traceId`, `spanId`, `correlationId`,
   `tradeId`, `regionCode`, `service`, and `level` as keyword fields for
   exact-match queries.

---

### Requirement 4: Trace-Log Correlation

**User Story:** As a developer debugging an incident, I want to navigate from
a span in the tracing backend to its associated log lines and back so that I
do not have to manually copy `traceId` between tools.

#### Acceptance Criteria

1. EVERY `StructuredLog` entry SHALL carry the `traceId` and `spanId` from the
   active `OBSERVABILITY_TRACING` context (per
   `05-observability/01-otel-spring-boot` Requirement 2), so that all log
   lines for a span share the same `traceId`.
2. THE `OBSERVABILITY_LOGGING` UI `LogDashboard` (Requirement 5) SHALL include
   a deep-link template from a log entry's `traceId` to the corresponding
   trace in the tracing backend UI, so that a single click pivots to the trace.
3. WHEN no active trace context exists (e.g. a background scheduled task),
   THE `StructuredLog` SHALL set `traceId` and `spanId` to empty strings
   rather than omitting the fields, so that queries for missing `traceId` can
   identify untraced code paths.

---

### Requirement 5: Log Dashboards and Saved Queries

**User Story:** As a developer or operator, I want pre-built saved queries and
dashboards in the `OBSERVABILITY_LOGGING` UI for common investigation patterns
so that I can investigate incidents without writing queries from scratch.

#### Acceptance Criteria

1. THE `OBSERVABILITY_LOGGING` setup SHALL provision the following saved queries
   or `LogDashboard`s automatically:
   - **Trade timeline by tradeId**: filter all log lines for a given `tradeId`
     across all services, ordered by `timestamp`.
   - **Errors by service**: filter `level = ERROR` grouped by `service` over
     a time range.
   - **DLQ events**: filter log lines containing `dlq.origin.topic` field.
   - **Correlation ID trace**: filter all log lines for a given `correlationId`
     across all services.
2. ALL saved queries SHALL be provisioned as version-controlled configuration
   files under `DevOps/Local/OBSERVABILITY_LOGGING/saved-queries/` and SHALL
   be imported automatically at startup.
3. NO saved query or dashboard SHALL be created solely through the
   `OBSERVABILITY_LOGGING` UI; any manually created query MUST be exported and
   committed before the next environment rebuild.

---

### Requirement 6: Log Retention Policy

**User Story:** As a platform operator, I want a defined log retention policy
so that storage is bounded and operators know how far back they can query.

#### Acceptance Criteria

1. THE `LogStore` SHALL retain platform service logs for a minimum of **30
   days** in the local environment; retention SHALL be configurable via the
   `DevOps/Local/OBSERVABILITY_LOGGING/` configuration.
2. THE `LogStore` SHALL retain `EVENT_STREAM` broker logs for a minimum of
   **14 days**.
3. THE log retention configuration SHALL be version-controlled and SHALL NOT
   be modified through the `OBSERVABILITY_LOGGING` UI directly.
4. WHEN the local `LogStore` storage exceeds a configurable threshold, THE
   oldest log indices SHALL be deleted automatically by the configured
   retention policy; manual deletion SHALL NOT be required.
