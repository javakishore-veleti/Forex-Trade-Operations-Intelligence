# Requirements Document — Python Sidecars Local Deploy

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.

## Introduction

This feature defines the **local build, run, and wiring** of the four
`SIDECAR_LANGUAGE` detection/embedding packages scaffolded in phase 01
(`kpi-anomaly-detector`, `dlq-cluster-analyzer`, `capacity-forecast-model`,
`log-normalizer`). It covers containerized sidecar execution, the compact
anomaly envelope each sidecar emits, and the webhook trigger wiring from
each sidecar to the `AGENT_PLATFORM` so that a Python detection signal
becomes an agent run without high-volume data flowing through the agent.

Sidecars contain **no business logic**: they detect statistical anomalies or
normalize log formats, emit a compact envelope, and stop. All business
decisions, state changes, and risk computations remain in `SERVICE_FRAMEWORK`
services. No sidecar SHALL replicate, bypass, or replace any `SERVICE_FRAMEWORK`
function.

All identifiers in examples use the synthetic `FX-` prefix.

---

## Glossary

- **Sidecar**: A `SIDECAR_LANGUAGE` detection or embedding package running as
  a containerized process alongside the `SERVICE_FRAMEWORK` services.
- **AnomalyEnvelope**: The compact JSON output emitted by a `Sidecar` when a
  detection threshold is exceeded; compatible with `MCP_Tool_Contract` schema.
- **SidecarWebhook**: The `AGENT_PLATFORM` webhook URL that a `Sidecar` POSTs
  its `AnomalyEnvelope` to, triggering the corresponding agent workflow.
- **DetectionThreshold**: The configurable value above/below which a `Sidecar`
  considers an anomaly present; externalized as environment variable.
- **KPIAnomalyDetector**: The `kpi-anomaly-detector` sidecar — detects
  statistical deviations in per-region trade KPIs (capture rate, booking
  rate, settlement rate) against a calendar-aware baseline.
- **DLQClusterAnalyzer**: The `dlq-cluster-analyzer` sidecar — groups
  dead-lettered messages by failure signature using stack-trace clustering
  and embeddings.
- **CapacityForecastModel**: The `capacity-forecast-model` sidecar — forecasts
  processing completion time vs. cutoff given current backlog and throughput.
- **LogNormalizer**: The `log-normalizer` sidecar — normalizes raw log and
  event payloads into structured fact summaries for agent perception.

---

## Requirements

### Requirement 1: Sidecar Container Build and Run

**User Story:** As a developer running the platform locally, I want each sidecar
containerized and startable via the `DevOps/Local/` compose stack so that
detection sidecars run alongside services without manual Python environment setup.

#### Acceptance Criteria

1. EACH `Sidecar` SHALL have a `CONTAINER_RUNTIME` image definition (per
   `01-initial-setup/02-repo-skeleton` Requirement 6) that builds a
   production-ready image installing the sidecar's `SIDECAR_LANGUAGE` package
   via the `SIDECAR_BUILD_BACKEND`.
2. THE `DevOps/Local/` compose stack SHALL include a service entry for each
   of the four `Sidecar`s, using the `CONTAINER_RUNTIME` image built from the
   `Sidecars/` directory.
3. EACH `Sidecar` compose service SHALL declare: the image built from
   `Sidecars/{sidecar-name}/`; environment variables for
   `DETECTION_THRESHOLD`, `WEBHOOK_URL` (the `SidecarWebhook` endpoint), and
   any data-source connection parameters; and a `healthcheck` that confirms
   the sidecar process is running.
4. EACH `Sidecar` SHALL connect to data sources via environment-variable
   configured endpoints; no data-source hostname or port SHALL be hard-coded.
5. WHEN the `SidecarWebhook` URL is unreachable, THE `Sidecar` SHALL log a
   WARN-level entry and continue polling; it SHALL NOT crash on a single
   webhook delivery failure.

---

### Requirement 2: Anomaly Envelope Schema

**User Story:** As the `AGENT_PLATFORM` agent receiving a sidecar trigger, I
want every sidecar to emit a consistent `AnomalyEnvelope` so that the webhook
handler can parse any sidecar's output without sidecar-specific logic.

#### Acceptance Criteria

1. EVERY `Sidecar` SHALL emit an `AnomalyEnvelope` JSON object with the
   following fields when a `DetectionThreshold` is exceeded:
   `detectorName` (string, the sidecar name), `detectedAt` (ISO-8601),
   `severity` (`LOW` | `MEDIUM` | `HIGH`), `summary` (≤ 200 character
   human-readable description), `affectedEntities` (list of
   `{entityType, entityId}` using `SyntheticData`), `evidence` (list of
   key-value metric snapshots), and `webhookTriggerWorkflow` (the
   `AGENT_PLATFORM` workflow name to trigger).
2. THE `AnomalyEnvelope` `affectedEntities` field SHALL use `FX-` prefixed
   `tradeId`s and fictional service/region names; no real identifiers SHALL
   appear.
3. THE `AnomalyEnvelope` SHALL NOT include raw log text, full stack traces,
   or any field that could contain PII or production credentials.
4. THE `AnomalyEnvelope` structure SHALL be compatible with the
   `MCP_Tool_Contract` `ToolEnvelope` schema defined in `shared-mcp-contracts`
   so that agents can parse sidecar outputs and service tool outputs with
   the same parsing logic.

---

### Requirement 3: KPI Anomaly Detector Specifics

**User Story:** As a platform operator, I want the `KPIAnomalyDetector` to
detect when per-region trade processing KPIs deviate significantly from their
calendar-aware baseline so that business slowdowns are surfaced before EOD.

#### Acceptance Criteria

1. THE `KPIAnomalyDetector` SHALL poll per-region KPI metrics (capture rate,
   booking rate, settlement rate) from the `OBSERVABILITY_METRICS` or
   `SERVICE_FRAMEWORK` actuator endpoints at a configurable interval.
2. THE `KPIAnomalyDetector` SHALL maintain a rolling calendar-aware baseline
   (same day-of-week, same regional calendar context) and flag deviations
   exceeding the `DETECTION_THRESHOLD` as anomalies.
3. WHEN an anomaly is detected, THE `KPIAnomalyDetector` SHALL emit an
   `AnomalyEnvelope` and POST it to the configured `SidecarWebhook` URL.
4. THE detector SHALL NOT make any risk or business decisions; it emits a
   signal only.

---

### Requirement 4: DLQ Cluster Analyzer Specifics

**User Story:** As a platform operator, I want the `DLQClusterAnalyzer` to
group dead-lettered messages by failure signature so that the DLQ triage agent
receives pre-clustered input rather than raw individual messages.

#### Acceptance Criteria

1. THE `DLQClusterAnalyzer` SHALL read dead-lettered messages from `DLQTopic`s
   via the `EVENT_STREAM` consumer API at a configurable interval.
2. THE `DLQClusterAnalyzer` SHALL cluster messages by failure signature using
   stack-trace similarity (deterministic string matching or lightweight
   embedding-based clustering).
3. WHEN cluster sizes exceed the `DETECTION_THRESHOLD`, THE
   `DLQClusterAnalyzer` SHALL emit an `AnomalyEnvelope` summarizing each
   cluster: signature, message count, example `tradeId`s (synthetic), and
   recommended action hint (`auto-replay` or `quarantine`).
4. THE `DLQClusterAnalyzer` SHALL NOT replay messages itself; replay is an
   `AGENT_PLATFORM` gated action.

---

### Requirement 5: Capacity Forecast Model and Log Normalizer Specifics

**User Story:** As a platform operator, I want the `CapacityForecastModel` and
`LogNormalizer` sidecars running locally so that the capacity-backlog and
lifecycle-reconstruction agents have sidecar-produced inputs available.

#### Acceptance Criteria

1. THE `CapacityForecastModel` SHALL poll current backlog size and throughput
   rate from `SERVICE_FRAMEWORK` metrics endpoints and compute an estimated
   completion time vs. the next regional cutoff.
2. WHEN the forecast indicates completion will exceed the cutoff by more than
   a configurable margin, THE `CapacityForecastModel` SHALL emit an
   `AnomalyEnvelope` with the forecast details (estimated completion time,
   cutoff time, shortfall in minutes) and POST it to the `SidecarWebhook`.
3. THE `LogNormalizer` SHALL consume structured log entries from the
   `OBSERVABILITY_LOGGING` backend for a given `tradeId` or `correlationId`
   and emit a normalized fact summary (an `AnomalyEnvelope` with
   `detectorName = log-normalizer`) suitable for agent perception.
4. THE `LogNormalizer` output SHALL redact or omit any field that could
   contain PII, real credentials, or non-synthetic identifiers.

---

### Requirement 6: Sidecar-to-Agent Webhook Wiring

**User Story:** As an agent developer, I want each sidecar's `AnomalyEnvelope`
to trigger the corresponding `AGENT_PLATFORM` workflow automatically so that
detection-to-agent latency is minimized without high-volume data flowing
through the agent.

#### Acceptance Criteria

1. EACH `Sidecar` SHALL POST its `AnomalyEnvelope` to the `SidecarWebhook`
   URL configured via the `WEBHOOK_URL` environment variable; the URL SHALL
   point to the `AGENT_PLATFORM`'s webhook trigger endpoint for the
   corresponding workflow.
2. THE webhook POST SHALL be a simple HTTP POST with `Content-Type:
   application/json` and the `AnomalyEnvelope` as the body; no authentication
   token is required in the local environment (a placeholder comment SHALL
   mark where auth will be added in cloud deploy).
3. THE `Sidecar` SHALL retry the webhook POST up to **3 times** with a **2
   second** backoff before logging a failure and moving on; it SHALL NOT
   block detection polling while waiting for a webhook response.
4. THE webhook wiring configuration (URL per sidecar) SHALL be documented in
   `DevOps/Local/AGENT_PLATFORM/sidecar-webhooks.md` and SHALL reference the
   synthetic workflow names (e.g. `business-kpi-guard`, `dlq-triage-agent`).
5. ALL example payloads and documentation in the webhook wiring docs SHALL use
   `SyntheticData` (`FX-` prefixed `tradeId`s, fictional sidecar outputs).
