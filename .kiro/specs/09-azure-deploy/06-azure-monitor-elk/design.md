# Design Document — Azure Monitor + Log Analytics (Observability on Azure)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). Resolves `CloudTargetBinding`
> for `OBSERVABILITY_LOGGING` → Azure Monitor + Log Analytics. Concrete configuration.

## 1. Overview

The platform `OBSERVABILITY_LOGGING` maps to **Azure Monitor** (Log Analytics workspace +
Application Insights + Azure Monitor Metrics). The OTel Collector deployed as a DaemonSet
receives OTLP from services and exports to the Azure Monitor backend. This replaces the local
ELK stack while preserving all observability capabilities.

**Technology Bindings:**

| Technology Role | Azure Service | Configuration |
|---|---|---|
| `OBSERVABILITY_LOGGING` | Azure Log Analytics Workspace | 30-day hot, 90-day archive |
| `OBSERVABILITY_TRACING` | Application Insights (workspace-based) | Connected to Log Analytics |
| `OBSERVABILITY_METRICS` | Azure Monitor Metrics | Custom metrics namespace |
| Pipeline | OTel Collector (DaemonSet) | OTLP receiver → Azure Monitor exporter |
| Dashboards | Azure Workbooks / Grafana (Azure Managed) | 8 dashboards migrated |
| Alerts | Azure Monitor Alert Rules | 6 rules migrated |

## 2. Log Analytics Workspace

```bicep
// DevOps/Azure/bicep/modules/monitor/main.bicep (conceptual)
resource workspace 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: 'fxops-logs-${environment}'
  location: location
  properties: {
    sku: { name: environment == 'prod' ? 'PerGB2018' : 'PerGB2018' }
    retentionInDays: 30
    features: {
      enableLogAccessUsingOnlyResourcePermissions: true
    }
    workspaceCapping: {
      dailyQuotaGb: environment == 'prod' ? -1 : 5   // -1 = unlimited; dev capped at 5GB/day
    }
  }
}

// Archive tier for extended retention
resource archiveTable 'Microsoft.OperationalInsights/workspaces/tables@2022-10-01' = {
  name: 'ContainerLogV2'
  parent: workspace
  properties: {
    totalRetentionInDays: 365    // 30 hot + 335 archive
    plan: 'Analytics'
  }
}
```

## 3. Application Insights

```bicep
resource appInsights 'Microsoft.Insights/components@2020-02-02' = {
  name: 'fxops-ai-${environment}'
  location: location
  kind: 'web'
  properties: {
    Application_Type: 'web'
    WorkspaceResourceId: workspace.id
    IngestionMode: 'LogAnalytics'
    publicNetworkAccessForIngestion: 'Enabled'   // OTel Collector in-cluster sends here
    publicNetworkAccessForQuery: 'Enabled'
  }
}
```

## 4. OTel Collector DaemonSet

```yaml
# DevOps/Azure/helm/platform/otel-collector/values.yaml
mode: daemonset
config:
  receivers:
    otlp:
      protocols:
        grpc: { endpoint: "0.0.0.0:4317" }
        http: { endpoint: "0.0.0.0:4318" }
  processors:
    batch:
      timeout: 10s
      send_batch_size: 1024
    resource:
      attributes:
        - key: service.namespace
          value: fxops
          action: upsert
  exporters:
    azuremonitor:
      connection_string: ${APPLICATIONINSIGHTS_CONNECTION_STRING}
      instrumentation_key: ${APPINSIGHTS_INSTRUMENTATION_KEY}
    azuremonitor/logs:
      endpoint: https://dc.services.visualstudio.com/v2/track
  service:
    pipelines:
      traces:
        receivers: [otlp]
        processors: [batch, resource]
        exporters: [azuremonitor]
      metrics:
        receivers: [otlp]
        processors: [batch, resource]
        exporters: [azuremonitor]
      logs:
        receivers: [otlp]
        processors: [batch, resource]
        exporters: [azuremonitor/logs]
```

Services set `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector.fxops-infra:4317`.

## 5. KQL Queries (Migrated from ELK Saved Queries)

### Query 1: Trade Lifecycle Trace (replaces Kibana "Trade Journey")
```kql
AppTraces
| where Properties.tradeId == "FX-000001"
| project TimeGenerated, ServiceName=Properties.service, Message, SeverityLevel
| order by TimeGenerated asc
```

### Query 2: Error Correlation (replaces Kibana "Error Cluster")
```kql
AppExceptions
| where TimeGenerated > ago(1h)
| summarize Count=count() by ServiceName=AppRoleName, ExceptionType=Type, bin(TimeGenerated, 5m)
| order by Count desc
```

### Query 3: EOD Completion Monitor (replaces Kibana "EOD Timeline")
```kql
AppTraces
| where Properties.eventType startswith "EOD_"
| extend region = tostring(Properties.region)
| summarize LatestEvent=max(TimeGenerated) by region, EventType=tostring(Properties.eventType)
| order by region, LatestEvent asc
```

### Query 4: Consumer Lag Dashboard (replaces Kibana "Kafka Lag")
```kql
AppMetrics
| where Name == "kafka.consumer.lag"
| extend consumerGroup = tostring(Properties.consumerGroup), topic = tostring(Properties.topic)
| summarize MaxLag=max(Sum) by consumerGroup, topic, bin(TimeGenerated, 1m)
| where MaxLag > 1000
```

## 6. Alert Rules (Migrated from Grafana)

| Alert | KQL Condition | Severity | Action |
|---|---|---|---|
| Trade throughput drop | `AppMetrics \| Name=="trade.captured.count" \| rate < 50%` | Sev2 | Email + Teams |
| Risk latency breach | `AppMetrics \| Name=="risk.calculation.duration" \| avg > 5000ms` | Sev2 | Email |
| EOD timeout | `No EOD_GLOBAL_COMPLETE within 2h of scheduled` | Sev1 | PagerDuty |
| Consumer lag critical | `kafka.consumer.lag > 10000 for 5m` | Sev2 | Email + Teams |
| Error spike | `AppExceptions count > 100 in 5m` | Sev2 | Email |
| Service unhealthy | `Pod restart > 3 in 10m` | Sev3 | Teams |

## 7. Diagnostic Settings (Platform Resources)

```bicep
resource diagnosticSetting 'Microsoft.Insights/diagnosticSettings@2021-05-01-preview' = {
  name: 'send-to-workspace'
  scope: aksCluster
  properties: {
    workspaceId: workspace.id
    logs: [
      { categoryGroup: 'allLogs', enabled: true }
    ]
    metrics: [
      { category: 'AllMetrics', enabled: true }
    ]
  }
}
// Repeated for: PostgreSQL, Event Hub, Redis, Cosmos DB
```

Container Insights enabled via AKS addon (`omsagent`).

## 8. Azure Managed Grafana (Optional)

```bicep
resource grafana 'Microsoft.Dashboard/grafana@2023-09-01' = {
  name: 'fxops-grafana-${environment}'
  location: location
  properties: {
    grafanaIntegrations: {
      azureMonitorWorkspaceIntegrations: [{ azureMonitorWorkspaceResourceId: workspace.id }]
    }
  }
}
```

Allows reusing existing Grafana dashboard JSON with Azure Monitor as data source.

## 9. Bicep Module Layout

```
DevOps/Azure/bicep/modules/monitor/
├── main.bicep              ← workspace + retention + capping
├── app-insights.bicep      ← Application Insights resource
├── alerts.bicep            ← 6 alert rules
├── diagnostics.bicep       ← diagnostic settings for all resources
├── otel-collector/         ← Helm values for OTel Collector DaemonSet
└── workbooks/              ← Azure Workbook JSON (8 dashboards)
```
