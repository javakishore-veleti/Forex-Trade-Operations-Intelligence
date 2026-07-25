# Tasks — Log Correlation (Cross-Cutting Logging Pipeline)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific files/config, and is independently verifiable.
> This is a **living checklist** — mark `[x]` as each task is completed and verified. Tags in
> parentheses trace to design sections (§) and requirements (Req / GP-Rq). This is a **cross-cutting
> infrastructure/config** spec: tasks touch shared logging config and `DevOps/Local/OBSERVABILITY_LOGGING/`,
> not a new microservice. It **consumes** GP-Rq-2 `correlationId` and the `01-otel-spring-boot` trace
> context — it does not generate either.

## 0. Shared logging config surface
- [ ] 0.1 Create the shared logging config location (`shared-domain-contracts`/`shared-observability` module resources) that every `Middleware/` service inherits via the parent build — no per-service copies. (§1, §7)
- [ ] 0.2 Add the JSON-log encoder dependency (structured JSON Logback encoder) to the shared/parent build so all services render JSON, not free text. *(product resolved from technology-stack: `OBSERVABILITY_LOGGING`/`SERVICE_FRAMEWORK`)* (Req 1.1)
- [ ] 0.3 **Verify:** a service inheriting the shared config starts and emits one-line JSON to stdout (visually confirm a startup line is a JSON object, not free text).

## 1. Structured logging config (shared) (Req 1)
- [ ] 1.1 Shared Logback config: root appender uses the JSON encoder emitting `timestamp` (ISO-8601 UTC per `SERIALIZATION`), `level`, `service` (`spring.application.name`), `logger`, `message`. (§2)
- [ ] 1.2 Add MDC fields to the encoder output: `%X{correlationId}`, `%X{traceId}`, `%X{spanId}`, `%X{tradeId}`, `%X{regionCode}` as top-level JSON fields (never embedded in `message`). (§2, Req 1.3/1.4)
- [ ] 1.3 Route stack traces to a structured `exception` field only; keep `message` free of stack traces. (§2, Req 1.5)
- [ ] 1.4 Static context fields: bind `service` and `region`/`regionCode` per service without per-service Logback duplication. (§2, Req 1.4)
- [ ] 1.5 Unit/slice test: capture an emitted line, assert it is valid JSON carrying `correlationId` **and** `traceId` as top-level fields, `tradeId` present when set, and stack trace confined to `exception`. **Verify:** test green. (§9, Req 1.2/1.5)

## 2. MDC enrichment — trace + business context (Req 4; consumes GP-Rq-2)
- [ ] 2.1 Enable the `OBSERVABILITY_TRACING` → MDC bridge so the active `traceId`/`spanId` land in the MDC before each log line and clear on scope close. (§3, Req 4.1)
- [ ] 2.2 Empty-string fallback: guarantee `traceId`/`spanId` MDC keys render as `""` (present, not omitted) when no active span exists. (§3, Req 4.3)
- [ ] 2.3 `tradeId` enrichment: put `tradeId` into the MDC at the trade-scoped unit-of-work boundary (same boundary GP-Rq-2 uses for `correlationId`); do **not** re-implement correlation-id generation — consume it. (§2, §3, Req 1.3)
- [ ] 2.4 Test: a log emitted with no active span produces `traceId:""`/`spanId:""`, never omitted. **Verify:** test green. (§9, Req 4.3)

## 3. Logstash pipeline (LogPipeline) (Req 2)
- [ ] 3.1 Author the Logstash pipeline under `DevOps/Local/OBSERVABILITY_LOGGING/`, version-controlled and auto-loaded when the local logging stack starts. (§4, Req 2.4)
- [ ] 3.2 Input: collect all `Service_Module` container logs via the `CONTAINER_RUNTIME` logging driver or file-based shipping; second input for `EVENT_STREAM` broker logs. (§4, Req 2.1/2.5)
- [ ] 3.3 Parse: `json` filter decodes structured lines; no grok/free-text re-parsing of already-structured fields. (§4, Req 2.2)
- [ ] 3.4 Enrich: add `environment` (e.g. `local`) at ingest; type `traceId`/`spanId`/`correlationId`/`tradeId`/`regionCode`/`service`/`level` as keyword. (§4, Req 2.3)
- [ ] 3.5 Output: index service logs to `fxops-services-YYYY.MM.dd` and broker logs to `fxops-kafka-YYYY.MM.dd`. (§4, Req 2.5)
- [ ] 3.6 **Verify:** with the `DevOps/Local/` stack up, a structured line ships through and appears in a `fxops-services-*` index with the `environment` field added.

## 4. Kibana index patterns (Req 3)
- [ ] 4.1 Provision `fxops-services-*` and `fxops-kafka-*` index patterns as version-controlled files auto-imported at startup (no UI-only creation). (§5, Req 3.1/3.2)
- [ ] 4.2 Set `timestamp` as the default time field; index `traceId`, `spanId`, `correlationId`, `tradeId`, `regionCode`, `service`, `level` as keyword fields on `fxops-services-*`. (§5, Req 3.3)
- [ ] 4.3 **Verify:** on a clean environment the two index patterns exist without manual steps and keyword fields are exact-match queryable.

## 5. Kibana saved searches / dashboards (Req 5)
- [ ] 5.1 Provision saved searches under `DevOps/Local/OBSERVABILITY_LOGGING/saved-queries/`, auto-imported at startup: **Trade timeline by tradeId** (order by `timestamp`), **Correlation ID trace** (by `correlationId`), **Errors by service** (`level=ERROR` grouped by `service`), **DLQ events** (`dlq.origin.topic`). (§5, Req 5.1/5.2)
- [ ] 5.2 Add the `traceId` deep-link template from a log entry to the tracing-backend UI (log → trace pivot). (§5, §3, Req 4.2)
- [ ] 5.3 **Verify:** all saved searches load from committed files on a clean rebuild; none exist only in the UI. (Req 5.3)

## 6. Retention policy (Req 6)
- [ ] 6.1 Version-controlled index-lifecycle config under `DevOps/Local/OBSERVABILITY_LOGGING/`: retain `fxops-services-*` ≥ **30 days** (configurable), `fxops-kafka-*` ≥ **14 days**. (§6, Req 6.1/6.2/6.3)
- [ ] 6.2 Auto-delete oldest date-suffixed indices when a configurable storage threshold is exceeded — no manual deletion. (§6, Req 6.4)
- [ ] 6.3 **Verify:** retention config applies from committed files (not the UI); confirm the delete phase is bound to the `fxops-*` index families.

## 7. Validation — cross-service correlation (Req 4, 5; GP-Rq-12/14)
- [ ] 7.1 Integration (`INTEGRATION_TEST_HARNESS`: Elasticsearch + Logstash): ship synthetic `FX-000001` logs from two distinct `service` values through the real pipeline; query `fxops-services-*` by `tradeId:"FX-000001"` → assert both services' lines returned, ordered by `timestamp`. (§9, Req 5.1)
- [ ] 7.2 Integration: query by `correlationId` → assert the full cross-service set returned (correlation-id trace). (§9, Req 5.1)
- [ ] 7.3 Assert every shipped line carries `correlationId` **and** `traceId` and that structured fields are typed as keyword with `environment` added at ingest. (§9, Req 1.2/2.2/2.3)
- [ ] 7.4 All fixtures use synthetic `FX-` ids and fictional names only. (§9, GP-Rq-14)

## 8. Verification & tracking
- [ ] 8.1 Bring up the `DevOps/Local/` logging stack and run the pipeline integration tests — all green. (§9)
- [ ] 8.2 Update `.kiro/specs/MASTER-PLAN.md`: mark `05-observability/04-otel-log-correlation` design+tasks complete.
- [ ] 8.3 Commit via `scripts/commit-specs.sh` / normal commit.

---
**Completion:** 0 / 26 tasks. Update this line as tasks are ticked.
