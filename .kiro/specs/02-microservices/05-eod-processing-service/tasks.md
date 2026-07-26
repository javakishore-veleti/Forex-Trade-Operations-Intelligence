# Tasks — End-of-Day Processing (Bounded Context)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific files, and is independently verifiable.
> This is a **living checklist** — mark `[x]` as each task is completed and verified. Tags in
> parentheses trace to design sections (§) and requirements (Req / GP-Rq).

## 0. Module scaffold
- [x] 0.1 Create Maven module `Middleware/eod-processing-service` with `<parent>` → `Middleware/pom.xml`; add to parent `<modules>`. (§2)
- [x] 0.2 Add dependencies: `shared-domain-contracts`, Spring Web, Spring Data JPA + PostgreSQL driver, Spring Kafka, Flyway, Actuator, Micrometer/OTel, Testcontainers (test), JUnit 5 (test), MockMvc (test). *(products resolved from technology-stack)* (§1)
- [x] 0.3 `EodProcessingApplication` (`@SpringBootApplication`) + `application.yml` (`spring.application.name=eod-processing-service`, `eod.region-order=APAC,EMEA,AMERICAS`, unprocessed-trade tolerance, peer endpoints, topics). (§2, §3, GP-Rq-11)
- [x] 0.4 Context-load test asserting the application context starts. **Verify:** `mvn -pl Middleware/eod-processing-service test` green.

## 1. Domain — regional close + readiness (Req 2, 3)
- [x] 1.1 `domain/RegionalCloseStatus` enum + immutable `PERMITTED` transition table (`IN_PROGRESS/BLOCKED/READY/CLOSED`). (§4)
- [x] 1.2 `domain/Blocker`, `BlockerType`, `ReadinessResult`, `ReadinessInputs`, `ReadinessStatusMap`, `RegionOrdering` (configurable close order). (§3, §5)
- [x] 1.3 `domain/ReadinessEvaluator.evaluate(ReadinessInputs)` as a **pure function**: `READY` only when all four Req 3 inputs satisfied; else `BLOCKED` with specific unmet conditions. (§5, Req 3.1–3.3)
- [x] 1.4 Unit tests: each unmet input yields the right `BLOCKED` reason; all-satisfied yields `READY`; tolerance-with-approved-exception path; determinism (same inputs → same result). **Verify:** unit tests green. (Req 7.1, GP-Rq-13)

## 2. Persistence — relational state + audit (Req 2, 5, 6; GP-Rq-6)
- [x] 2.1 Entities `RegionalCloseEntity`, `BranchCompletionEntity`, `BlockerEntity`, `ConsolidationEntity` (fields per §8) each with `@Version`. (GP-Rq-6)
- [x] 2.2 Append-only `EodAuditEntity` + `ProcessedEventEntity` (dedup marker). (§8, Req 5.3, GP-Rq-5.3)
- [x] 2.3 Spring Data JPA repositories for all entities; append-only semantics on `EodAuditEntity` (insert only). (§8, Req 5.3)
- [x] 2.4 Flyway migrations: `regional_close`, `branch_completion`, `blocker`, `consolidation` (PK `business_date`), `eod_audit`, `processed_event`, with the unique constraints per §8. **Verify:** migrations apply on a Testcontainers Postgres. (§8, Req 6.4)

## 3. Integration — peer service clients (Req 1, 3; GP-Rq-10)
- [x] 3.1 `integration/BusinessCalendarClient.currentGlobalBusinessDate()` + booking-date classification lookup (REST). (§3, Req 1.1/1.4)
- [x] 3.2 `integration/RiskCalculationClient.snapshotExists(region, businessDate)` (REST). (§5, Req 3.1)
- [x] 3.3 `config/RestClientConfig`: bounded timeouts + bounded backoff retry on both peers. (§11, GP-Rq-10)

## 4. Application — branch, readiness, blockers (Req 2, 3, 4)
- [x] 4.1 `application/BranchCompletionService.markComplete/read`: idempotent upsert keyed on `(businessDate,region,branch)`. (§4, Req 2.2/2.3/2.5)
- [x] 4.2 `application/ReadinessService`: collect the four inputs (branches, unprocessed count+tolerance, risk snapshot via client, open blockers), invoke pure `ReadinessEvaluator`, persist + return status; `rerun(region)`. (§5, §7, Req 3, Req 5.1)
- [x] 4.3 `application/BlockerService`: track `LATE_TRADE` candidate blockers (materiality deferred), expose region blocker list, keep region `BLOCKED` until resolved/cleared. (§6, Req 4.1–4.3)
- [x] 4.4 `application/ExceptionService.recordException(region, blockerId, approvalReference)`: reject blank ref (`400`); never auto-approve; append audit row. (§7, Req 4.4, Req 5.2/5.3/5.4)

## 5. Application — global consolidation (Req 6; GP-Rq-5, GP-Rq-7)
- [x] 5.1 `application/ConsolidationService.consolidate(businessDate)`: if already `CLOSED` → return existing result without re-executing (idempotent by date). (§10, Req 6.4)
- [x] 5.2 Not-ready guard: if any prerequisite region not `READY` → `409` `NotReadyConflict` listing regions + blockers. (§10, Req 6.2)
- [x] 5.3 All-ready path in one transaction: regions=`CLOSED`, `GLOBAL`=`CLOSED`, consolidation row (contributing snapshots + applied exceptions), `CONSOLIDATED` audit row, publish `EodCompletedEvent` via transactional producer. (§10, §9.2, Req 6.3/6.5, GP-Rq-7)

## 6. Consumer + producer — readiness signals & completion event (Req 3, 6; GP-Rq-7)
- [x] 6.1 `config/KafkaConsumerConfig` (manual ack, concurrency=partitions) + `KafkaProducerConfig` (transactional producer). (§9)
- [x] 6.2 `consumer/ReadinessSignalConsumer` `@KafkaListener` on risk-snapshot + booking-date topics: correlation-id → dedup → record input → **ack only after tx commit**. (§9.1, Req 3.1, GP-Rq-7.3)
- [x] 6.3 `event/EodCompletedEvent` (shared-contract type; `eventId`/`correlationId`/`sourceService`/`occurredAt`/`businessDate`) published in the consolidation tx. (§9.2, Req 6.3, GP-Rq-7.4)

## 7. API — commands + reads (Req 2, 3, 5, 6; GP-Rq-1)
- [x] 7.1 `api/EodCommandController`: `POST branches/{region}/{branchId}/complete`, `POST regions/{region}/rerun`, `POST regions/{region}/exceptions`, `POST consolidate` (all under `/api/v1`). (§9.3, Req 2.2/5.1/5.2/6.1)
- [x] 7.2 `api/EodQueryController`: `GET branches/{region}`, `GET readiness` (Readiness Status Map + `GLOBAL`), `GET regions/{region}/blockers`, `GET consolidation` — all side-effect free. (§9.3, Req 2.3/3.4/3.5, GP-Rq-1.4)

## 8. Golden-path realizations (inherited NFRs → concrete) (§11)
- [x] 8.1 `web/CorrelationIdFilter` + consumer MDC copy + `%X{correlationId}` log pattern; set on published event. (GP-Rq-2)
- [x] 8.2 `web/GlobalExceptionHandler` (`@RestControllerAdvice`) → `400`/`404`/`409`/`500` envelopes, no stack traces in body. (GP-Rq-3)
- [x] 8.3 `health/ReadinessHealthIndicator` checks Postgres + Kafka assignment + Business Calendar + Risk Calculation reachability. (GP-Rq-4)
- [x] 8.4 `config/SecurityConfig` permit-all + standard Phase-6 auth TODO marker. (GP-Rq-9)
- [x] 8.5 Business metrics `eod_region_readiness{region,status}`, `eod_consolidation_total{outcome}`, `eod_blockers_open{region}` via Micrometer. (GP-Rq-8)

## 9. Tests — domain scenarios (Req 7; GP-Rq-12)
- [x] 9.1 Web-layer tests: endpoints incl. `400` blank `approvalReference`, `409` not-ready consolidation, `404` unknown region. (GP-Rq-12.1, Req 5.2)
- [x] 9.2 Integration (Testcontainers Postgres+Kafka, peers stubbed): region reaches `READY` only when all Req 3 inputs satisfied, `BLOCKED` otherwise. (Req 7.1)
- [x] 9.3 Integration: `consolidate` returns `409` while any region not `READY`; succeeds only when every prerequisite region `READY` → regions+`GLOBAL` `CLOSED` + `EodCompletedEvent` published. (Req 7.2)
- [x] 9.4 Integration: repeat `consolidate` for an already-`CLOSED` `Global Business Date` returns existing result without re-executing. (Req 7.3)
- [x] 9.5 Test: applying an `Exception` without an `approvalReference` is rejected. (Req 7.4)
- [x] 9.6 All fixtures use synthetic `FX-` ids and fictional branch/region names. (GP-Rq-14)

## 10. Verification & tracking
- [x] 10.1 `mvn -pl Middleware/eod-processing-service verify` — build + all tests green.
- [x] 10.2 Update `MASTER-PLAN.md`: mark `05-eod-processing-service` design+tasks+code complete.
- [x] 10.3 Commit via `scripts/commit-specs.sh` / normal commit.

---
**Completion:** 41 / 41 tasks. Update this line as tasks are ticked.
