# Tasks — Azure Monitor + Log Analytics (Observability on Azure)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Execute top-to-bottom;
> each task is atomic and independently verifiable. Mark `[x]` as completed.

## 0. Scaffold

- [ ] 0.1 Create `DevOps/Azure/bicep/modules/monitor/` with `main.bicep`, `app-insights.bicep`, `alerts.bicep`, `diagnostics.bicep`. (§9)
- [ ] 0.2 Create `DevOps/Azure/helm/platform/otel-collector/` with Helm values for OTel Collector DaemonSet. (§4)
- [ ] 0.3 Create `DevOps/Azure/bicep/modules/monitor/workbooks/` for dashboard JSON. (§9)

## 1. Log Analytics Workspace (Req 1)

- [ ] 1.1 Define Log Analytics workspace resource: `PerGB2018` SKU, 30-day retention.
- [ ] 1.2 Configure daily ingestion cap for dev (5 GB/day), unlimited for prod.
- [ ] 1.3 Configure archive tier for `ContainerLogV2` table (365-day total retention for prod).
- [ ] 1.4 Enable resource-context access control.
- [ ] 1.5 Output workspace ID and resource ID for downstream diagnostic settings. **Verify:** `az bicep build` succeeds.

## 2. Application Insights (Req 3)

- [ ] 2.1 Define Application Insights resource (workspace-based, connected to Log Analytics).
- [ ] 2.2 Configure application type as `web`.
- [ ] 2.3 Output instrumentation key and connection string for OTel Collector.
- [ ] 2.4 Enable Smart Detection (failure anomalies, performance). **Verify:** App Insights in Bicep.

## 3. OTel Collector DaemonSet (Req 2)

- [ ] 3.1 Create Helm values file for OTel Collector DaemonSet in `fxops-infra` namespace.
- [ ] 3.2 Configure OTLP receiver (gRPC 4317, HTTP 4318).
- [ ] 3.3 Configure Azure Monitor exporter with App Insights connection string.
- [ ] 3.4 Configure batch processor (timeout 10s, batch size 1024).
- [ ] 3.5 Define service pipelines: traces, metrics, logs → Azure Monitor exporter.
- [ ] 3.6 Document service-side config: `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector.fxops-infra:4317`. **Verify:** `helm template` renders valid DaemonSet.

## 4. KQL Queries (Req 5)

- [ ] 4.1 Write KQL query: Trade Lifecycle Trace (filter by `tradeId`, order by time).
- [ ] 4.2 Write KQL query: Error Correlation (exceptions grouped by service + type per 5m bucket).
- [ ] 4.3 Write KQL query: EOD Completion Monitor (EOD events by region + time).
- [ ] 4.4 Write KQL query: Consumer Lag Dashboard (kafka.consumer.lag metric, threshold > 1000).
- [ ] 4.5 Package queries as Log Analytics Query Pack or Workbook tiles. **Verify:** KQL syntax validates in portal (or documented syntax).

## 5. Dashboards / Workbooks (Req 4)

- [ ] 5.1 Create Azure Workbook JSON for: Trade Throughput dashboard.
- [ ] 5.2 Create Workbook for: Risk Calculation Latency dashboard.
- [ ] 5.3 Create Workbook for: EOD Processing Status dashboard.
- [ ] 5.4 Create Workbook for: Consumer Lag & Kafka Health dashboard.
- [ ] 5.5 Create Workbooks for remaining 4 dashboards (service health, error rate, cache performance, DB performance).
- [ ] 5.6 Document RBAC for dashboard access (resource group scoped). **Verify:** Workbook JSON files valid.

## 6. Alert Rules (Req 4)

- [ ] 6.1 Define alert: Trade throughput drop > 50% over 5m → Sev2.
- [ ] 6.2 Define alert: Risk calculation latency avg > 5000ms → Sev2.
- [ ] 6.3 Define alert: EOD global not complete within 2h of scheduled → Sev1.
- [ ] 6.4 Define alert: Consumer lag > 10000 for 5m → Sev2.
- [ ] 6.5 Define alert: Exception spike > 100 in 5m → Sev2.
- [ ] 6.6 Define alert: Pod restart > 3 in 10m → Sev3.
- [ ] 6.7 Configure action groups (email, Teams webhook). **Verify:** alert rules in Bicep.

## 7. Diagnostic Settings (Req 6)

- [ ] 7.1 Create diagnostic setting for AKS cluster → workspace (allLogs + AllMetrics).
- [ ] 7.2 Create diagnostic setting for PostgreSQL Flexible Server → workspace.
- [ ] 7.3 Create diagnostic setting for Event Hub namespace → workspace.
- [ ] 7.4 Create diagnostic setting for Azure Cache for Redis → workspace.
- [ ] 7.5 Create diagnostic setting for Cosmos DB account → workspace.
- [ ] 7.6 Connect Azure Activity Log for the resource group → workspace. **Verify:** diagnostic settings in Bicep.

## 8. Container Insights (Req 6)

- [ ] 8.1 Confirm Container Insights enabled via AKS addon (`omsagent`).
- [ ] 8.2 Verify node-level and pod-level metrics flowing to workspace.
- [ ] 8.3 Document Prometheus scraping (Azure Monitor managed Prometheus if needed). **Verify:** addon enabled in AKS Bicep.

## 9. Cost and Security (Req 7)

- [ ] 9.1 Configure commitment tier pricing for prod (evaluate 100 GB/day level).
- [ ] 9.2 Set dev daily cap to 5 GB.
- [ ] 9.3 Configure data export to storage account for long-term audit retention (≥ 1 year).
- [ ] 9.4 Tag all resources with `project:fxops`, `environment:<env>`. **Verify:** tags + cap in Bicep.
