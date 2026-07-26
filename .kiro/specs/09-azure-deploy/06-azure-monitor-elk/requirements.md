# Requirements Document — Azure Monitor + Log Analytics (Observability on Azure)

> **Technology-agnostic spec.** References `OBSERVABILITY_LOGGING` Technology Role from
> `01-initial-setup/01-technology-stack`. Resolves via `CloudTargetBinding` → Azure Monitor + Log Analytics.
> Inherits `architecture-golden-path/01-service-nfrs`.

## Introduction

This spec defines requirements for the managed observability platform replacing the local ELK stack
on Azure. Azure Monitor with Log Analytics workspace, Application Insights, and the OTel-to-Azure
Monitor pipeline provides log aggregation, distributed tracing, metrics collection, and KQL-based
querying. All example identifiers use synthetic `FX-` prefixes.

---

## Glossary

- **ObservabilityLogging**: The `CloudTargetBinding` for `OBSERVABILITY_LOGGING` on Azure → Azure Monitor + Log Analytics.
- **LogAnalyticsWorkspace**: Central repository for log and metric data, queryable via KQL.
- **ApplicationInsights**: Application performance management (APM) component for traces, requests, dependencies.
- **KQL**: Kusto Query Language — the query syntax for Log Analytics and Application Insights.
- **OTelPipeline**: The OpenTelemetry Collector pipeline that exports spans, metrics, and logs to Azure Monitor.
- **DiagnosticSetting**: Azure resource configuration that routes platform logs/metrics to Log Analytics.

---

## Requirements

### Requirement 1: Log Analytics Workspace

**User Story:** As an operator, I want a centralized log repository with structured querying,
so that cross-service investigation is fast and correlated.

#### Acceptance Criteria

1. A Log Analytics workspace SHALL be provisioned in the same region as the AKS cluster.
2. ALL application logs from `Middleware/` services SHALL be ingested into the workspace.
3. THE workspace SHALL retain logs for at least 30 days (hot) with archive tier for 90+ days.
4. LOG ingestion SHALL support structured JSON logs with fields: `traceId`, `spanId`, `tradeId`, `region`, `service`, `level`, `message`.
5. THE workspace SHALL support KQL queries for cross-service correlation by `traceId` and `tradeId`.

---

### Requirement 2: OTel → Azure Monitor Pipeline

**User Story:** As a service developer, I want the existing OTel instrumentation to export
seamlessly to Azure Monitor, so that no application instrumentation changes are required.

#### Acceptance Criteria

1. THE platform SHALL deploy an OpenTelemetry Collector as a DaemonSet or sidecar that receives OTLP from services.
2. THE Collector SHALL export traces to Application Insights via the Azure Monitor exporter.
3. THE Collector SHALL export metrics to Azure Monitor Metrics (custom metrics namespace).
4. THE Collector SHALL export logs to Log Analytics workspace.
5. THE OTel SDK configuration in services SHALL change only the exporter endpoint (OTLP → Collector) — no instrumentation library changes.

---

### Requirement 3: Application Insights (Distributed Tracing)

**User Story:** As a developer, I want distributed traces visualized with dependency mapping,
so that latency and failures across service boundaries are diagnosable.

#### Acceptance Criteria

1. AN Application Insights resource SHALL be provisioned and connected to the Log Analytics workspace.
2. TRACES SHALL include all service-to-service calls, database calls, and Kafka produce/consume spans.
3. THE Application Map SHALL show dependency topology between all Middleware services and data stores.
4. END-TO-END transaction search SHALL support filtering by `tradeId` custom dimension.
5. FAILURE and performance anomaly detection (Smart Detection) SHALL be enabled.

---

### Requirement 4: Metrics and Dashboards

**User Story:** As an operator, I want operational dashboards equivalent to local Grafana,
so that key business and infrastructure KPIs are visible.

#### Acceptance Criteria

1. CUSTOM metrics (trade throughput, risk calculation latency, EOD completion %, consumer lag) SHALL be published to Azure Monitor Metrics.
2. AZURE Workbooks or Dashboards SHALL replicate the 8 Grafana dashboards from the local observability spec.
3. METRIC alerts SHALL be configured for the 6 alert rules from the local spec (trade throughput drop, risk latency breach, EOD timeout, etc.).
4. DASHBOARD access SHALL be RBAC-controlled via Azure resource groups.

---

### Requirement 5: KQL Queries (ELK Saved Queries Migration)

**User Story:** As an operator, I want the 4 saved Kibana queries migrated to KQL equivalents,
so that existing investigation workflows are preserved.

#### Acceptance Criteria

1. THE platform SHALL provide KQL equivalents for all 4 saved ELK queries from `05-observability/04-otel-log-correlation`.
2. KQL queries SHALL be saved as Log Analytics query packs or workbook tiles for one-click execution.
3. QUERIES SHALL support parameterization (time range, tradeId, region, service name).
4. CORRELATION queries SHALL join across traces, logs, and custom metrics using `traceId`.

---

### Requirement 6: Platform Diagnostics

**User Story:** As a platform engineer, I want Azure resource diagnostics flowing into the same
workspace, so that infrastructure and application signals are co-located.

#### Acceptance Criteria

1. DIAGNOSTIC settings SHALL be configured on: AKS cluster, PostgreSQL Flexible Server, Event Hub namespace, Azure Cache for Redis, Cosmos DB account.
2. ALL diagnostic categories (audit, operational, data-plane) SHALL route to the shared Log Analytics workspace.
3. AZURE Activity Log for the resource group SHALL be connected to the workspace.
4. CONTAINER Insights SHALL be enabled on the AKS cluster for node/pod-level metrics and logs.

---

### Requirement 7: Security and Cost

**User Story:** As a security and FinOps stakeholder, I want observability data secured and
costs predictable.

#### Acceptance Criteria

1. THE Log Analytics workspace SHALL use workspace-based access control (RBAC).
2. SENSITIVE log fields (if any) SHALL be masked or excluded before ingestion.
3. DAILY ingestion cap SHALL be configurable for dev environments to control costs.
4. PRODUCTION SHALL use commitment-tier pricing (100 GB/day or appropriate level) for cost efficiency.
5. DATA export to storage account SHALL be configured for long-term audit retention (≥ 1 year).
