# Tasks — Metrics and Dashboards (Cross-Cutting)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific config files under
> `DevOps/Local/OBSERVABILITY_METRICS/`, and is independently verifiable. This is a **living
> checklist** — mark `[x]` as each task is completed and verified. Tags in parentheses trace to
> design sections (§) and requirements (Req / GP-Rq). No runtime code — configuration only.

## 0. Config scaffold
- [ ] 0.1 Create `DevOps/Local/OBSERVABILITY_METRICS/` with the subtree from §1 (`targets/`, `alerts/`, `alertmanager/`, `grafana/provisioning/{datasources,dashboards}`, `grafana/dashboards/`). (§1, Rq-5)
- [ ] 0.2 Add Prometheus + Grafana + Alertmanager services to `DevOps/Local/` compose, mounting the config tree read-only; pin image tags (no `latest`). **Verify:** `docker compose config` parses; stack starts. (§1, GP-Rq-11)

## 1. Metric catalogue (Req 2, 3; GP-Rq-8)
- [ ] 1.1 Author the metric catalogue as a committed reference (technical §2.1 + business §2.2) mapping each metric → type, labels, owning service, dashboard/alert use. (§2)
- [ ] 1.2 Cross-check every business metric name against its owning service design (e.g. `lifecycle_transitions_total` in `03-trade-lifecycle-service`); flag any name this spec consumes that no service emits. **Verify:** every catalogue business metric traces to a service design that declares it. (§2.2)

## 2. Scrape configuration (Req 1)
- [ ] 2.1 `prometheus.yml`: `global` intervals (15s services / 30s infra, externalized), `rule_files`, `alerting` → Alertmanager, and two `file_sd` scrape jobs. (§3, Rq-1.1/1.3)
- [ ] 2.2 `targets/services.json`: one target per `Service_Module` in `Middleware/` with `service` label = `SERVICE_FRAMEWORK` app name and a `region` label; `metrics_path=/actuator/prometheus`. (§3, Rq-1.4/1.5)
- [ ] 2.3 `targets/infra.json`: `EVENT_STREAM` broker JMX/native exporter target with `service: kafka`. (§3, Rq-1.2)
- [ ] 2.4 **Verify:** `promtool check config prometheus.yml` passes; every service appears exactly once with a matching `service` label. (§7, Rq-1.4)

## 3. Per-service dashboards — provisioned JSON (Req 2, 5)
- [ ] 3.1 `grafana/provisioning/datasources/prometheus.yml` (Prometheus datasource) + `grafana/provisioning/dashboards/dashboards.yml` (file provider scanning `grafana/dashboards/`). (§4, Rq-5.1)
- [ ] 3.2 Common-row template (request rate, error rate, p50/p95/p99 latency, JVM memory/GC) reused by every service dashboard. (§4, Rq-2.2)
- [ ] 3.3 `svc-trade-ingest.json`: common row + `trades_captured_total`, `trade_validation_failures_total{reason}`, idempotency hit rate. (§4, Rq-2.3)
- [ ] 3.4 `svc-trade-lifecycle.json`: common row + `lifecycle_transitions_total{from,to}`, illegal-transition rate, duplicate-event rate. (§4, Rq-2.4)
- [ ] 3.5 `svc-risk-calculation.json`: common row + `risk_calculations_total{region,risk_level}`, `risk_calculation_duration_seconds` histogram, `fallback_rule_firings_total` rate. (§4, Rq-2.5)
- [ ] 3.6 `svc-eod-processing.json`: common row + per-region `eod_region_status` (0-3), `eod_branch_completion_ratio`, `eod_time_to_close_seconds`. (§4, Rq-2.6)
- [ ] 3.7 `svc-business-calendar.json` and `svc-state-reconciliation.json`: common row only. (§4, Rq-2.1)
- [ ] 3.8 `svc-event-sequence-processor.json`: common row + `sequence_violations_total{violation_type}`, `sequence_facts_active` gauge. (§4, Rq-2.7)
- [ ] 3.9 **Verify:** each dashboard JSON parses; references only catalogue metric names; example labels are synthetic `FX-` ids. (§7, GP-Rq-14)

## 4. Global business-health dashboard (Req 3)
- [ ] 4.1 `platform-business-health.json`: total captures/min across regions, overall error rate, global EOD status for current `GlobalBusinessDate`, per-region risk throughput. (§4, Rq-3.1/3.4)
- [ ] 4.2 `dlq_depth{topic}` time-series per `DLQTopic` with a horizontal threshold line at the alert value; `sequence_violations_total` grouped by `violation_type`. (§4, Rq-3.2/3.3)
- [ ] 4.3 **Verify:** dashboard parses; all example label values are synthetic `FX-` / fictional regions. (§7, Rq-3.5)

## 5. Alert rules (Req 4)
- [ ] 5.1 `alerts/platform-alert-rules.yml` with externalized threshold template values at top. (§5, Rq-4.7)
- [ ] 5.2 Rules: `ServiceErrorRateHigh` (>5%/2m), `DlqDepthHigh` (>10/5m), `PoisonMessageQuarantined` (immediate), `RiskLatencyHigh` (p95>2s/5m), `EodCloseBlocked` (BLOCKED>30m), `SequenceViolationSpike` (>10/min/2m). (§5, Rq-4.1-4.6)
- [ ] 5.3 Operational rules: `TradeThroughputDrop`, `EodCompletionStalled`, `ConsumerLagHigh`; each annotation carries the labels named in §5. (§5)
- [ ] 5.4 **Verify:** `promtool check rules alerts/platform-alert-rules.yml` passes; `promtool test rules` confirms `ServiceErrorRateHigh` fires at 6%/2m and stays silent at 4%. (§7, Rq-4)

## 6. Anomaly-envelope routing to n8n (§6)
- [ ] 6.1 `alertmanager/alertmanager.yml`: grouping/dedup + a webhook receiver routing firing alerts to the n8n `AGENT_PLATFORM` as a compact anomaly envelope (alert, labels, summary, severity, firedAt) — never raw metrics. (§6, GP-Rq-13)
- [ ] 6.2 **Verify:** `amtool check-config alertmanager/alertmanager.yml` passes; a synthetic fired alert yields exactly one envelope at the webhook. (§7)

## 7. Validation & tracking
- [ ] 7.1 **Verify:** bring up the local `CONTAINER_RUNTIME` stack; Grafana provisions all 8 dashboards (7 service + 1 global) with no import errors, each bound to the Prometheus datasource. (§7, Rq-5.1)
- [ ] 7.2 Update `MASTER-PLAN.md`: mark `05-observability/03-otel-metrics-dashboards` design+tasks+config complete.
- [ ] 7.3 Commit via `scripts/commit-specs.sh` / normal commit.

---
**Completion:** 0 / 22 tasks. Update this line as tasks are ticked.
