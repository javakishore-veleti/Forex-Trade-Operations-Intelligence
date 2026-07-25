# Tasks — OTel Spring Boot Instrumentation (Cross-Cutting)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific files, and is independently verifiable.
> This is a **living checklist** — mark `[x]` as each task is completed and verified. Tags in
> parentheses trace to design sections (§) and requirements (Req / GP-Rq).
>
> **Cross-cutting spec** — no new business logic and no new service module. Work is a **shared
> wiring module** plus a **per-service convention** applied to every existing `Middleware/`
> `SERVICE_FRAMEWORK` service. Kafka header propagation is **out of scope** (see
> `05-observability/02-otel-kafka-tracing`).

## 0. Shared observability module scaffold
- [ ] 0.1 Create Maven module `Middleware/shared-observability` (packaging `jar`, a convention library — not runnable), `<parent>` → `Middleware/pom.xml`; add to parent `<modules>`. (§2)
- [ ] 0.2 Add dependencies: Spring Boot autoconfigure, Micrometer Tracing + OTel bridge starter, OTLP exporter, Actuator + Micrometer Prometheus registry. *(products resolved from technology-stack: `OBSERVABILITY_TRACING`, `OBSERVABILITY_METRICS`)* (§1.1)
- [ ] 0.3 Register `ObservabilityAutoConfiguration` via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`; add `application-observability.yml` with shared exporter/actuator defaults. (§2)
- [ ] 0.4 Context-load test asserting the auto-configuration contributes its beans. **Verify:** `mvn -pl Middleware/shared-observability test` green.

## 1. Resource attributes + service identity (Req 1.2)
- [ ] 1.1 `ResourceAttributesContributor` setting `service.name` = `spring.application.name`, `service.region` = `FXOPS_REGION`, `service.version`, `deployment.environment` = active profile. (§3.2)
- [ ] 1.2 Unit test: emitted span carries `service.name` and `service.region` (never a hard-coded literal). **Verify:** unit test green with an in-memory span exporter. (Req 1.2)

## 2. W3C TraceContext propagation over HTTP (Req 2)
- [ ] 2.1 `W3cPropagationConfig` pinning the propagator to W3C (`traceparent`/`tracestate`) for extract + inject; apply to auto-configured `RestClient`/`WebClient`/`RestTemplate` builders. (§4, Req 2.1)
- [ ] 2.2 Attach the existing `CorrelationId` (GP-Rq-2) to every span as attribute `correlation.id`. (§4, Req 2.4)
- [ ] 2.3 Unit tests: inbound `traceparent` → trace **continued** (same trace id, child span); no `traceparent` → **new root**. **Verify:** unit tests green. (Req 2.2/2.3)

## 3. Business-context baggage (Req 4)
- [ ] 3.1 `BaggageConfig` registering exactly `tradeId`, `regionCode`, `correlationId` as baggage fields; allow-list rejects any other key. (§5, Req 4.4)
- [ ] 3.2 `BusinessContextObservationFilter` copying present baggage → span attributes `trade.id` / `region.code` / `correlation.id` on every span in the unit of work. (§5, Req 4.1/4.2)
- [ ] 3.3 Confirm baggage auto-propagates on outbound HTTP so downstream services inherit context. (§5, Req 4.3)
- [ ] 3.4 Unit tests: `tradeId=FX-000123` + `regionCode` set → span attrs `trade.id=FX-000123`, `region.code`; non-allowed key omitted. **Verify:** unit tests green; synthetic `FX-` ids only. (Req 4; GP-Rq-14)

## 4. Span naming + status conventions (Req 3, 5)
- [ ] 4.1 `SpanNamingConfig`: rename HTTP controller spans to `{service-name} {METHOD} {route-template}` (route template, not expanded path). (§3.1, Req 3.1)
- [ ] 4.2 Convention + helper for custom application spans `{service-name}/{domain-operation}` (e.g. via `@Observed`). (§3.1, Req 3.5)
- [ ] 4.3 Span status defaults: 500 → status `ERROR` + `error.type`, no stack-trace attribute; 4xx → status `OK` + `http.response.status_code`. (§6, Req 5.1/5.2/5.3)
- [ ] 4.4 Unit tests: HTTP span renamed per convention; custom span named per convention; 500 → `ERROR`/no-stack-trace; 404 → `OK`. **Verify:** unit tests green. (Req 3, 5)

## 5. Exporter configuration per environment (Req 1.3, 1.5, 6; GP-Rq-11, GP-Rq-10)
- [ ] 5.1 Externalize the OTLP endpoint as `OTEL_EXPORTER_OTLP_ENDPOINT` in `application-observability.yml`; profile resolution local/aws/azure; **never hard-coded**. (§7, Req 1.3)
- [ ] 5.2 Configure parent-based sampler + `OTEL_TRACES_SAMPLER*` via env (full local, ratio in cloud). (§7)
- [ ] 5.3 Fire-and-forget exporter with bounded queue; collector-unreachable logs WARN and never surfaces as a service error. (§7, Req 1.5; GP-Rq-10)
- [ ] 5.4 `DevOps/Local/` compose: add `OTelCollector` + local tracing UI, pinned image tags (never `latest`); document UI port in `DevOps/Local/README.md`. (§7, Req 6.1/6.3)

## 6. Per-service wiring convention (Req 1.1)
- [ ] 6.1 Attach the `OTelAgent` at JVM startup via `JAVA_TOOL_OPTIONS`/`-javaagent` in the `DevOps/Local/` compose + `SERVICE_BUILD_TOOL` run config — not baked into any service jar. (§2, Req 1.1)
- [ ] 6.2 For every `Middleware/` service: add the `shared-observability` dependency and `spring.config.import: classpath:application-observability.yml`; set each service's local compose exporter endpoint to the local `OTelCollector`. (§2, Req 6.2)
- [ ] 6.3 Confirm auto-instrumentation captures inbound/outbound HTTP, `RELATIONAL_STORE`, `DOCUMENT_STORE`, `CACHE`, and `EVENT_STREAM` producer/consumer spans (Kafka header trace-through owned by `02-otel-kafka-tracing`). **Verify:** run one service with the agent; the listed span kinds appear in the local UI. (§6, Req 1.4)

## 7. Verification tests (Req 2, 4; GP-Rq-12)
- [ ] 7.1 Web-layer test (`WEB_LAYER_TEST`) in one host service: `GET /api/v1/trades/{tradeId}/state` → one server span named per convention with `service.name`, `service.region`, `trade.id=FX-…`, `correlation.id`. (§10)
- [ ] 7.2 Integration test (`INTEGRATION_TEST_HARNESS`): two-service HTTP hop → **one trace, two spans**, child adopts parent trace id, baggage present on both. (§10, Req 2/4.3)
- [ ] 7.3 Local smoke: `DevOps/Local/` collector + UI up; drive one synthetic `FX-` request; trace visible with expected attributes. (§10, Req 6)
- [ ] 7.4 All fixtures use synthetic `FX-` ids and fictional service names only. (GP-Rq-14)

## 8. Verification & tracking
- [ ] 8.1 `mvn -pl Middleware/shared-observability verify` — build + all instrumentation tests green.
- [ ] 8.2 Update `MASTER-PLAN.md`: mark `05-observability/01-otel-spring-boot` design+tasks complete (Status column).
- [ ] 8.3 Commit via `scripts/commit-specs.sh` / normal commit.

---
**Completion:** 0 / 26 tasks. Update this line as tasks are ticked.
