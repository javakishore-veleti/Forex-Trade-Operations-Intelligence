# Tasks — Python Sidecars Local Deploy

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific files, and is independently verifiable.
> This is a **living checklist** — mark `[x]` as each task is completed and verified. Tags in
> parentheses trace to design sections (§) and requirements (Req). All fixtures use synthetic
> `FX-` ids and fictional region/service names.

## 1. Shared sidecar template + envelope schema (§3)
- [ ] 1.1 Shared `config.py`: read `DETECTION_THRESHOLD`, `WEBHOOK_URL`, `POLL_INTERVAL`, and data-source endpoints from env vars only — nothing hard-coded; fail fast on missing required vars. (§3.1, Req 1.4)
- [ ] 1.2 Shared `envelope.py`: `AnomalyEnvelope` dataclass with `detectorName`, `detectedAt` (ISO-8601), `severity` (`LOW|MEDIUM|HIGH`), `summary` (≤200 chars), `affectedEntities[{entityType,entityId}]`, `evidence[{key,value}]`, `webhookTriggerWorkflow`; `to_json()`; a guard that rejects raw-text/stack-trace/PII fields. (§3.2, Req 2.1–2.4)
- [ ] 1.3 Shared `webhook.py` `WebhookEmitter`: HTTP POST envelope JSON to `WEBHOOK_URL`; retry 3× with 2 s backoff; WARN + continue on failure (non-blocking, no crash); `# TODO(cloud-deploy): add auth header` placeholder. (§4.2, Req 6.2/6.3, Req 1.5)
- [ ] 1.4 Shared `main.py` poll-loop template: read source → detect → (if anomaly) emit → sleep `POLL_INTERVAL`; each sidecar wires its own `detector.py`. (§3.1, §4). **Verify:** `test_envelope.py` (shape ⊆ `ToolEnvelope`, no forbidden fields) and `test_webhook.py` (3×/2 s retry, unreachable → WARN+continue) green as a shared template suite.

## 2. `kpi-anomaly-detector` — business-KPI seasonal anomalies (Req 3)
- [ ] 2.1 Package: `pyproject.toml` (PEP 621, `requires-python ">=3.11"`, `hatchling.build` backend), `src/kpi_anomaly_detector/__init__.py` exposing `__version__`, copy shared `config/envelope/webhook/main`. (§3.1)
- [ ] 2.2 `detector.py` stub: poll per-region capture/booking/settlement rates from metrics/actuator; maintain a rolling **calendar-aware seasonal baseline** (same day-of-week / regional calendar); flag deviations > `DETECTION_THRESHOLD`. Detection only — no risk/business decision. (§2, Req 3.1/3.2/3.4)
- [ ] 2.3 Emit + wire webhook with `webhookTriggerWorkflow = "business-kpi-guard"` (feeds catalog #9 Business KPI Guard). (§5, Req 3.3)
- [ ] 2.4 `Dockerfile`: slim Python base, install package via Hatchling, ENTRYPOINT = poll loop. (§3.1, Req 1.1)
- [ ] 2.5 Tests: synthetic per-region KPI series → detector fires above threshold, silent below; envelope valid; `FX-` ids only. **Verify:** `python -m pytest Sidecars/kpi-anomaly-detector` green. (§6.2)

## 3. `dlq-cluster-analyzer` — stack-trace clustering (Req 4)
- [ ] 3.1 Package: `pyproject.toml` + `src/dlq_cluster_analyzer/__init__.py` (`__version__`), copy shared `config/envelope/webhook/main`. (§3.1)
- [ ] 3.2 `detector.py` stub: read-only consume DLQ topics via `EVENT_STREAM`; cluster by failure signature (deterministic string match / lightweight embedding); flag clusters whose count > `DETECTION_THRESHOLD`; emit signature + count + example synthetic `tradeId`s + action hint (`auto-replay`|`quarantine`). Never replays. (§2, Req 4.1–4.4)
- [ ] 3.3 Emit + wire webhook with `webhookTriggerWorkflow = "dlq-triage-agent"` (feeds catalog #17 DLQ Triage). (§5)
- [ ] 3.4 `Dockerfile`: slim Python base, Hatchling install, ENTRYPOINT = poll loop. (§3.1, Req 1.1)
- [ ] 3.5 Tests: synthetic DLQ messages with fabricated signatures → correct clustering + fire on oversized cluster; envelope carries no raw stack traces. **Verify:** `python -m pytest Sidecars/dlq-cluster-analyzer` green. (§6.2, Req 2.3)

## 4. `capacity-forecast-model` — completion-vs-cutoff forecast (Req 5)
- [ ] 4.1 Package: `pyproject.toml` + `src/capacity_forecast_model/__init__.py` (`__version__`), copy shared `config/envelope/webhook/main`. (§3.1)
- [ ] 4.2 `detector.py` stub: poll backlog size + throughput from `SERVICE_FRAMEWORK` metrics; forecast estimated completion vs next regional cutoff; flag when completion exceeds cutoff by > configurable margin; emit estimated completion, cutoff, shortfall-minutes. (§2, Req 5.1/5.2)
- [ ] 4.3 Emit + wire webhook with `webhookTriggerWorkflow = "capacity-backlog-agent"` (feeds catalog #19 Consumer-Lag / #16 Backlog Planning). (§5)
- [ ] 4.4 `Dockerfile`: slim Python base, Hatchling install, ENTRYPOINT = poll loop. (§3.1, Req 1.1)
- [ ] 4.5 Tests: synthetic backlog/throughput points → forecast crosses/does-not-cross cutoff drives fire/silent; shortfall math correct. **Verify:** `python -m pytest Sidecars/capacity-forecast-model` green. (§6.2)

## 5. `log-normalizer` — perception fact summaries (Req 5)
- [ ] 5.1 Package: `pyproject.toml` + `src/log_normalizer/__init__.py` (`__version__`), copy shared `config/envelope/webhook/main`. (§3.1)
- [ ] 5.2 `detector.py` stub: consume structured log entries from `OBSERVABILITY_LOGGING` for a `tradeId`/`correlationId`; emit a normalized fact-summary envelope (`detectorName = log-normalizer`); redact/omit any PII, real credential, or non-synthetic id at read time. (§2, §4.1, Req 5.3/5.4)
- [ ] 5.3 Emit + wire webhook with `webhookTriggerWorkflow = "trade-lifecycle-agent"` (feeds catalog #1 Lifecycle Reconstruction, perception). (§5)
- [ ] 5.4 `Dockerfile`: slim Python base, Hatchling install, ENTRYPOINT = poll loop. (§3.1, Req 1.1)
- [ ] 5.5 Tests: synthetic log lines with planted PII-shaped fields → output redacts them; fact summary well-formed; `FX-` ids only. **Verify:** `python -m pytest Sidecars/log-normalizer` green. (§6.2, Req 2.3/5.4)

## 6. Compose wiring, verification & tracking (§4, §6.2)
- [ ] 6.1 `DevOps/Local/` compose: a service entry per sidecar (image from `Sidecars/{name}/`, env `DETECTION_THRESHOLD`/`WEBHOOK_URL`/source params, `healthcheck`); document per-sidecar URLs + synthetic workflow names in `DevOps/Local/AGENT_PLATFORM/sidecar-webhooks.md`. (Req 1.2/1.3, 6.4/6.5)
- [ ] 6.2 **Verify (pytest):** `python -m pytest Sidecars/` — all four sidecars' tests green, synthetic `FX-` fixtures only.
- [ ] 6.3 **Verify (docker build):** `docker build` each `Sidecars/{name}/Dockerfile` succeeds and the container entry point starts the poll loop. (Req 1.1)
- [ ] 6.4 Update `MASTER-PLAN.md`: mark `06-local-deploy/02-python-sidecars` design+tasks complete.
- [ ] 6.5 Commit via `scripts/commit-specs.sh` / normal commit.

---
**Completion:** 0 / 29 tasks. Update this line as tasks are ticked.
