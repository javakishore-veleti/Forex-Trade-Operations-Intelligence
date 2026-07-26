# Tasks — Trade Lifecycle (Bounded Context)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific files, and is independently verifiable.
> This is a **living checklist** — mark `[x]` as each task is completed and verified. Tags in
> parentheses trace to design sections (§) and requirements (Req / GP-Rq).

## 0. Module scaffold
- [x] 0.1 Create Maven module `Middleware/trade-lifecycle-service` with `<parent>` → `Middleware/pom.xml`; add to parent `<modules>`. (§2)
- [x] 0.2 Add dependencies: `shared-domain-contracts`, Spring Web, Spring Data JPA + PostgreSQL driver, Spring Data MongoDB, Spring Kafka, Spring Data Redis, Actuator, Micrometer/OTel, Testcontainers (test). *(products resolved from technology-stack)* (§1)
- [x] 0.3 `TradeLifecycleApplication` (`@SpringBootApplication`) + `application.yml` (`spring.application.name=trade-lifecycle-service`). (§2)
- [x] 0.4 Context-load test asserting the application context starts. **Verify:** `mvn -pl Middleware/trade-lifecycle-service test` green.

## 1. Domain — state machine (Req 1)
- [x] 1.1 `domain/StateMachine` with the immutable `PERMITTED` transition table and terminal statuses. (§3)
- [x] 1.2 `StateMachine.canTransition(from,to)` + `targetFor(TradeEventType)` induction map, as pure functions. (§3, Req 1.6)
- [x] 1.3 Unit tests: every permitted transition passes; representative illegal transitions fail; each event type maps to the right status. **Verify:** unit tests green. (Req 7.1)

## 2. Persistence — relational current state (Req 2, 5; GP-Rq-6)
- [x] 2.1 `persistence/relational/TradeCurrentStateEntity` (fields per §4 table) with `@Version`. (GP-Rq-6)
- [x] 2.2 `TradeCurrentStateRepository` (Spring Data JPA).
- [x] 2.3 Schema migration for `trade_current_state` (PK `trade_id`, `version`). **Verify:** migration applies on a Testcontainers Postgres.

## 3. Persistence — document audit history (Req 4)
- [x] 3.1 `persistence/document/AuditEntryDocument` (fields per §4 JSON). (Req 4.2)
- [x] 3.2 `AuditRepository` (Spring Data Mongo) with append-only semantics (insert only). (Req 4.3)
- [x] 3.3 Indexes `{tradeId:1}` and `{tradeId:1, occurredAt:1}`; timeline query ordered by `occurredAt`, tie-break `recordedAt`. (Req 4.4/4.5)

## 4. Persistence — dedup cache (Req 6; GP-Rq-5)
- [x] 4.1 `persistence/cache/ProcessedEventStore` over Redis (`SET processed:{eventId}` + TTL); `seen(eventId)`/`mark(eventId)`. (§4)
- [x] 4.2 Relational fallback table `processed_events` when cache unavailable. (§4, §11)

## 5. Application — transition orchestration (Req 2, 3, 6, 7)
- [x] 5.1 `application/LifecycleService.process(TradeEvent)`: resolve target, load aggregate, decide via `StateMachine`, persist state + audit atomically. (§5, §8)
- [x] 5.2 Handle branches: new `TRADE_CAPTURED` → init aggregate; unknown-trade non-initiating → orphan audit, no aggregate; illegal → rejected audit, no state change; same-status → noop. (Req 2.2–2.4, 3, 6.2)
- [x] 5.3 `DedupService` integration: skip + noop-audit on duplicate `eventId`. (Req 6; GP-Rq-5)

## 6. Consumer — event ingestion (Req 2; GP-Rq-7)
- [x] 6.1 `consumer/TradeEventConsumer` `@KafkaListener("fxops.trade.events")`, manual ack, concurrency = partitions. (§5)
- [x] 6.2 Per-record flow: correlation-id copy to MDC → dedup → `LifecycleService.process` → **ack only after tx commit**; continue on illegal transition. (§5, Req 3.3, GP-Rq-7.3)

## 7. API — read models (Req 5; GP-Rq-1)
- [x] 7.1 `api/LifecycleQueryController` `/api/v1/trades/{tradeId}/state|timeline|expected-lifecycle` + DTOs; 404 on unknown. (§6)
- [x] 7.2 Timeline exposes `rejected/noop/orphan` flags; endpoints read-only. (Req 5.2/5.4/5.6)

## 8. Golden-path realizations (inherited NFRs → concrete) (§7)
- [x] 8.1 `web/CorrelationIdFilter` + consumer MDC copy + `%X{correlationId}` log pattern. (GP-Rq-2)
- [x] 8.2 `web/GlobalExceptionHandler` (`@RestControllerAdvice`) → 400/404/409/500 envelopes, no stack traces in body. (GP-Rq-3)
- [x] 8.3 `health/ReadinessHealthIndicator` checks Postgres + Mongo + Kafka assignment. (GP-Rq-4)
- [x] 8.4 `config/SecurityConfig` permit-all + standard Phase-6 auth TODO marker. (GP-Rq-9)
- [x] 8.5 Business metric `lifecycle_transitions_total{from,to,rejected}` via Micrometer. (GP-Rq-8)

## 9. Tests — domain scenarios (Req 7; GP-Rq-12)
- [x] 9.1 Web-layer tests: 3 query endpoints incl. 404 + anomaly-visible timeline. (GP-Rq-12.1)
- [x] 9.2 Integration (Testcontainers Postgres+Mongo+Kafka): `TRADE_CAPTURED→…→TRADE_SETTLED` → final `SETTLED` + ordered timeline. (Req 7.2)
- [x] 9.3 Integration: duplicate `eventId` → no extra transition + `noop` audit. (Req 7.3)
- [x] 9.4 Integration: orphan event → no aggregate created. (Req 7.4)
- [x] 9.5 All fixtures use synthetic `FX-` ids. (GP-Rq-14)

## 10. Verification & tracking
- [x] 10.1 `mvn -pl Middleware/trade-lifecycle-service verify` — build + all tests green.
- [x] 10.2 Update `MASTER-PLAN.md`: mark `03-trade-lifecycle-service` design+tasks+code complete.
- [x] 10.3 Commit via `scripts/commit-specs.sh` / normal commit.

---
**Completion:** 27 / 27 tasks. Update this line as tasks are ticked.
