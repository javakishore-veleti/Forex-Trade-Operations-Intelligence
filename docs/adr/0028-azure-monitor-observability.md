# ADR-0028: Azure Monitor + Managed Grafana over Self-Managed ELK on AKS

**Status:** Accepted

**Date:** 2024-02-21

## Context

The observability stack (metrics, logs, traces) needs an Azure deployment strategy. Locally, the platform uses Prometheus + Grafana (metrics), ELK (logs), and Jaeger (traces). For Azure, we must decide between self-managing the same stack on AKS or adopting Azure-native observability services.

Two approaches were evaluated:

1. **Azure Monitor** — Azure-native suite: Azure Monitor Metrics, Log Analytics (KQL), Application Insights for traces, Azure Managed Grafana for dashboards.
2. **Self-managed ELK + Prometheus + Jaeger on AKS** — same stack as local deployment on Kubernetes.

## Decision

We adopt a **hybrid approach**: Azure Monitor for metrics and traces, with Azure Managed Grafana for dashboards, and Log Analytics for log aggregation — replacing self-managed ELK/Prometheus/Jaeger.

### Implementation

| Concern | Azure Service | Local Equivalent |
|---------|---------------|------------------|
| Metrics | Azure Monitor Metrics + Prometheus (Container Insights) | Prometheus + Grafana |
| Dashboards | Azure Managed Grafana | Grafana |
| Logs | Log Analytics workspace (KQL) | Elasticsearch + Kibana |
| Traces | Application Insights (OpenTelemetry) | Jaeger |
| Alerts | Azure Monitor Alerts | Prometheus Alertmanager |

### OpenTelemetry Integration

- Services emit OpenTelemetry traces and metrics (same instrumentation as local)
- Azure Monitor OpenTelemetry exporter replaces Jaeger/Prometheus exporters
- Configuration change only — application code unchanged:
  ```yaml
  otel:
    exporter:
      otlp:
        endpoint: https://ingestion.monitor.azure.com
  ```

### Dashboard Migration

- 8 Grafana dashboards from local deployment imported directly into Azure Managed Grafana
- Data source changed from local Prometheus to Azure Monitor data source
- 6 alert rules migrated to Azure Monitor Alerts with Action Groups (PagerDuty, email)

### Log Query Migration

4 saved Kibana queries → KQL equivalents in Log Analytics:
- Trade processing latency P99 → `ContainerLog | where LogEntry contains "trade.processed" | summarize percentile(duration_ms, 99)`
- Service error rate → `ContainerLog | where LogLevel == "ERROR" | summarize count() by ServiceName, bin(TimeGenerated, 5m)`

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Self-managed ELK on AKS | 3-node Elasticsearch cluster requires ~24GB RAM; significant ops overhead; cost comparable to Log Analytics |
| Self-managed Prometheus + Jaeger | StatefulSet management, retention configuration, storage scaling — all handled by Azure Monitor |
| Datadog/New Relic | Third-party SaaS adds vendor dependency beyond cloud provider; per-host pricing expensive at scale |

## Consequences

### Positive
- Zero infrastructure management for observability (no Elasticsearch cluster, Prometheus StatefulSets)
- Native Azure integration: AKS container insights, ARM resource metrics, automatic correlation
- Managed Grafana preserves existing dashboard investment (same JSON format)
- KQL is more powerful than basic Kibana queries for log analytics
- Cost scales with data volume, not infrastructure provisioning

### Negative
- KQL syntax differs from Elasticsearch Query DSL — team learning curve
- Vendor lock-in to Azure Monitor for alerting and querying
- Log Analytics ingestion has slight delay (30-60s) vs real-time ELK
- Azure Managed Grafana has limited plugin support vs self-hosted

### Mitigations
- OTel instrumentation is vendor-neutral — traces/metrics can be re-routed to any backend
- KQL training materials provided; saved queries documented with equivalents
- For real-time debugging, `kubectl logs` and container stdout remain available
- Critical Grafana plugins verified compatible with Azure Managed Grafana tier
