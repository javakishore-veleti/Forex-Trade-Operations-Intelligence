# Requirements Document — Metrics and Dashboards

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.

## Introduction

This feature defines the **metrics scrape configuration, per-service dashboard
layout, and alert rules** for the `OBSERVABILITY_METRICS` role. Every
`Service_Module` exposes runtime metrics (per GP-Rq-8); this spec defines
*which metrics matter*, *how they are scraped*, *what dashboards visualize*,
and *when alerts fire*.

This spec has no new business logic. It configures the metrics layer for the
services and event infrastructure built in phases 02 and 03. All identifiers
in examples use the synthetic `FX-` prefix.

---

## Glossary

- **MetricsScrapeConfig**: The configuration that tells the `OBSERVABILITY_METRICS`
  collector which endpoints to scrape and at what interval.
- **ServiceDashboard**: A pre-built dashboard in the `OBSERVABILITY_METRICS`
  visualization layer scoped to one `Service_Module`.
- **PlatformDashboard**: A cross-service dashboard surfacing platform-wide
  health indicators (trade throughput, EOD status, DLQ depth).
- **AlertRule**: A threshold-based rule that fires a notification when a
  metric exceeds or falls below a defined condition for a defined duration.
- **BusinessMetric**: A metric with business meaning beyond raw infrastructure
  (e.g. `lifecycle_transitions_total`, `risk_calculations_total`,
  `dlq_depth`).
- **SLAMetric**: A latency or throughput metric that determines whether the
  platform meets a defined service-level objective.

---

## Requirements

### Requirement 1: Metrics Scrape Configuration

**User Story:** As a platform operator, I want the `OBSERVABILITY_METRICS`
collector configured to scrape every `Service_Module` and infrastructure
component so that all metrics are available in one place without manual setup.

#### Acceptance Criteria

1. THE `DevOps/Local/` compose configuration for `OBSERVABILITY_METRICS` SHALL
   include a scrape configuration file that declares a scrape job for every
   `Service_Module` in `Middleware/`, using the service's actuator metrics
   endpoint.
2. THE scrape configuration SHALL declare a scrape job for the `EVENT_STREAM`
   broker metrics endpoint (JMX exporter or native metrics endpoint).
3. THE scrape interval SHALL be **15 seconds** for all service jobs and **30
   seconds** for infrastructure jobs; both SHALL be externalized as
   configuration.
4. ALL scrape targets SHALL be identified by a `service` label matching the
   `SERVICE_FRAMEWORK` application name so that dashboard queries can filter
   by service without IP or port.
5. THE scrape configuration file SHALL be version-controlled under
   `DevOps/Local/OBSERVABILITY_METRICS/` and SHALL be the only place scrape
   targets are defined.

---

### Requirement 2: Per-Service Dashboards

**User Story:** As a developer or operator monitoring a specific service, I
want a pre-built dashboard for each `Service_Module` showing its request rate,
error rate, latency, and key business metrics, so that service health is visible
without writing custom queries.

#### Acceptance Criteria

1. A `ServiceDashboard` SHALL exist for each of the following services:
   `trade-ingest-service`, `trade-lifecycle-service`,
   `risk-calculation-service`, `eod-processing-service`,
   `business-calendar-service`, `state-reconciliation-service`, and
   `event-sequence-processor`.
2. EVERY `ServiceDashboard` SHALL include panels for: request rate (requests
   per second), error rate (percentage of `5xx` responses), p50/p95/p99
   latency, and JVM memory/GC metrics.
3. THE `trade-ingest-service` dashboard SHALL additionally include:
   `trades_captured_total` (counter), `trade_validation_failures_total`
   (counter by failure reason), and idempotency hit rate.
4. THE `trade-lifecycle-service` dashboard SHALL additionally include:
   `lifecycle_transitions_total{from,to}` (counter), illegal transition rate,
   and duplicate event rate.
5. THE `risk-calculation-service` dashboard SHALL additionally include:
   `risk_calculations_total{region,risk_level}` (counter),
   `risk_calculation_duration_seconds` (histogram), and fallback rule
   firing rate.
6. THE `eod-processing-service` dashboard SHALL additionally include:
   per-region close status (gauge: 0=IN_PROGRESS, 1=READY, 2=BLOCKED,
   3=CLOSED), branch completion percentage per region, and time-to-close
   per region.
7. THE `event-sequence-processor` dashboard SHALL additionally include:
   `sequence_violations_total{violation_type}` (counter) and active
   `SequenceFact` count (gauge).

---

### Requirement 3: Platform-Wide Dashboard

**User Story:** As a platform operator doing morning checks, I want a single
platform-wide dashboard showing overall trade throughput, EOD status, DLQ
depth, and active anomaly count so that I can assess platform health at a
glance.

#### Acceptance Criteria

1. A `PlatformDashboard` SHALL exist showing: total trade captures per minute
   (across all regions), overall error rate across all services, global EOD
   status for the current `GlobalBusinessDate`, and DLQ depth per topic.
2. THE `PlatformDashboard` SHALL show `dlq_depth{topic}` for every `DLQTopic`
   as a time-series panel, with a horizontal threshold line at the alert
   threshold value.
3. THE `PlatformDashboard` SHALL show `sequence_violations_total` as a
   time-series panel grouped by `violation_type`.
4. THE `PlatformDashboard` SHALL show per-region risk calculation throughput
   so that a region processing slowdown is visible relative to other regions.
5. ALL dashboard panels SHALL use only `SyntheticData` label values in
   example screenshots and documentation.

---

### Requirement 4: Alert Rules

**User Story:** As a platform operator, I want alert rules defined for critical
conditions so that I am notified of problems before they cause operational
impact rather than discovering them in post-mortems.

#### Acceptance Criteria

1. AN alert SHALL fire when any `Service_Module`'s error rate exceeds **5%**
   for more than **2 minutes**; the alert SHALL include the `service` label
   and the current error rate in the annotation.
2. AN alert SHALL fire when `dlq_depth{topic}` exceeds a configurable
   threshold (default **10 messages**) for more than **5 minutes**; the alert
   SHALL include the topic name.
3. AN alert SHALL fire immediately when any new `PoisonMessage` is quarantined
   (`dlq_poison_message_count` increases); the alert SHALL include the origin
   topic and the `tradeId` if available.
4. AN alert SHALL fire when the `risk_calculation_duration_seconds` p95 exceeds
   a configurable SLA threshold (default **2 seconds**) for more than
   **5 minutes**.
5. AN alert SHALL fire when a region's EOD close has been in `BLOCKED` state
   for more than a configurable duration (default **30 minutes**); the alert
   SHALL include the `regionCode` and `blockerCode`.
6. AN alert SHALL fire when `sequence_violations_total` increases by more than
   a configurable rate (default **10 violations per minute**) sustained for
   **2 minutes**.
7. ALL alert rules SHALL be defined in a version-controlled alert-rules
   configuration file under `DevOps/Local/OBSERVABILITY_METRICS/` and SHALL
   NOT be created manually in the metrics UI.

---

### Requirement 5: Dashboard and Alert Configuration as Code

**User Story:** As a platform engineer, I want all dashboards and alert rules
expressed as version-controlled configuration files so that the observability
setup is reproducible on a clean environment.

#### Acceptance Criteria

1. ALL `ServiceDashboard` and `PlatformDashboard` definitions SHALL be stored
   as JSON or YAML configuration files under
   `DevOps/Local/OBSERVABILITY_METRICS/dashboards/` and SHALL be provisioned
   automatically when the local metrics stack starts.
2. ALL `AlertRule` definitions SHALL be stored in a configuration file under
   `DevOps/Local/OBSERVABILITY_METRICS/alerts/` and SHALL be loaded by the
   metrics collector at startup.
3. NO dashboard or alert SHALL be created solely through the metrics UI; any
   manually created dashboard MUST be exported and committed to the repository
   before the next environment rebuild.
4. WHEN a new `Service_Module` is added to the platform, a corresponding
   `ServiceDashboard` configuration file SHALL be added in the same pull
   request.
