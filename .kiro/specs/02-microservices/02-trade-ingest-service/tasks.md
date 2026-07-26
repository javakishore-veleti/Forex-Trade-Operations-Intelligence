# Tasks — Trade Capture (Bounded Context)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from
> `design.md`. Execute top-to-bottom; each task is atomic and independently
> verifiable. Mark `[x]` as each task is completed.
> Tags trace to design sections (§) and requirements (Req / GP-Rq).

## 0. Module scaffold
- [x] 0.1 Create `Middleware/trade-ingest-service/pom.xml` with `<parent>` → `Middleware/pom.xml`; add dependencies: `shared-domain-contracts`, Spring Web, Spring Data JPA, PostgreSQL driver, Spring Data Redis (Lettuce), Spring Kafka, Flyway, Actuator, Micrometer/OTel, Testcontainers (test). (§1)
- [x] 0.2 Add `trade-ingest-service` to `<modules>` in `Middleware/pom.xml`.
- [x] 0.3 `TradeIngestApplication.java` (`@SpringBootApplication`) + `application.yml` (`spring.application.name=trade-ingest-service`).
- [x] 0.4 Context-load test `TradeIngestApplicationTest` asserting Spring context starts. **Verify:** `mvn -pl Middleware/trade-ingest-service test` green.

## 1. Domain — validation (Req 2)
- [x] 1.1 `domain/BusinessDayValidator.java` — pure function: `isWithinWindow(LocalDate tradeDate, LocalDate today, int maxBusinessDays)` using `DayOfWeek` weekend check; configurable fictional holiday set injected via constructor. (§4)
- [x] 1.2 Unit tests `BusinessDayValidatorTest`: tradeDate = today → valid; tradeDate = 4 business days ago → valid; tradeDate = 6 business days ago → invalid; weekend/holiday edge cases. **Verify:** tests green. (Req 5.2)

## 2. Persistence — schema and entity (Req 1 / GP-Rq-6)
- [x] 2.1 Flyway migration `V1__create_captured_trades.sql`: `captured_trades` table + `trade_id_seq` sequence + `idempotency_keys` table (schema per §6, §7). (§6, §7)
- [x] 2.2 `persistence/CapturedTradeEntity.java` — `@Entity` with all columns per §6 table; `@Version Long version`. (GP-Rq-6)
- [x] 2.3 `persistence/IdempotencyKeyEntity.java` — `@Entity` for `idempotency_keys` table (§7). (GP-Rq-5)
- [x] 2.4 `persistence/CapturedTradeRepository.java` — Spring Data JPA; add `findByIdempotencyKey` query. (§6)
- [x] 2.5 `persistence/IdempotencyKeyRepository.java` — Spring Data JPA. (§7)
- [x] 2.6 **Verify:** Flyway migration applies on a Testcontainers Postgres.

## 3. Application — trade ID generation (Req 1.3)
- [x] 3.1 `application/TradeIdGenerator.java` — `jdbcTemplate.queryForObject("SELECT nextval('trade_id_seq')", Long.class)` → `String.format("FX-%06d", seq)`. (§5)
- [x] 3.2 Unit tests `TradeIdGeneratorTest`: format assertion `FX-000001` through `FX-999999`; leading-zero padding verified. **Verify:** tests green. (Req 1.3)

## 4. Application — idempotency (Req 3 / GP-Rq-5)
- [x] 4.1 `application/IdempotencyService.java`: `check(key)` → Redis GET then DB fallback; `mark(key, response)` → DB INSERT then Redis SET TTL 24h. (§7)
- [x] 4.2 `config/RedisConfig.java` — Lettuce connection factory, `StringRedisTemplate`. (§1)
- [x] 4.3 Unit tests `IdempotencyServiceTest`: cache HIT returns cached response; cache MISS + DB HIT returns DB response; DB MISS returns empty. **Verify:** tests green.

## 5. Application — capture orchestration (Req 1, 3, 4 / GP-Rq-7)
- [x] 5.1 `api/dto/TradeRequest.java` — DTO with Bean Validation: `@NotNull @Valid CurrencyPair`, `@Positive BigDecimal notionalAmount`, `@NotBlank @Size(min=3,max=3) String notionalCurrency`, `@NotNull TradeDirection direction`, `@NotNull LocalDate tradeDate`, `@NotNull LocalDate valueDate`, `@NotBlank String counterpartyId`, `@NotBlank String tradingBookId`, `@NotNull RegionCode regionCode`. (§3, Req 2.1–2.9)
- [x] 5.2 `api/dto/TradeCaptureResponse.java` — record: `tradeId`, `correlationId`, `status`. (§3)
- [x] 5.3 `application/TradeCaptureService.java` — `capture(TradeRequest, idempotencyKey, correlationId)`: idempotency check → domain validation → `TradeIdGenerator.next()` → `@Transactional` block (persist entity + idempotency key + Kafka send) → Redis mark. (§8, Req 1, 3, 4)
- [x] 5.4 `config/KafkaProducerConfig.java` — transactional producer (`transaction-id-prefix=trade-ingest-`), JSON serializer, `fxops.trade.events` topic name from config. (§8)
- [x] 5.5 Unit tests `TradeCaptureServiceTest`: each validation rule independently rejects (no DB call asserted); valid request produces entity + event; idempotency replay returns same `tradeId`. **Verify:** tests green. (Req 5.1–5.3)

## 6. API — controller (Req 1 / GP-Rq-1)
- [x] 6.1 `api/TradeIngestController.java` — `@PostMapping("/api/v1/trades")`: read `X-Idempotency-Key` + `X-Correlation-Id` headers; `@Valid @RequestBody TradeRequest`; delegate to `TradeCaptureService`; return `ResponseEntity<TradeCaptureResponse>` 201 or 200 (replay). (§3)
- [x] 6.2 Web-layer tests `TradeIngestControllerTest` (MockMvc): 201 happy path; 400 for each validation rule failure (9 tests); 200 for idempotency replay. **Verify:** tests green. (Req 5.1, 5.2)

## 7. Golden-path realizations (inherited NFRs → concrete) (§9)
- [x] 7.1 `web/CorrelationIdFilter.java` — adopt `X-Correlation-Id` or generate UUID; store in MDC `correlationId`; set on response header. (GP-Rq-2)
- [x] 7.2 `web/GlobalExceptionHandler.java` (`@RestControllerAdvice`) — `MethodArgumentNotValidException` → 400 field errors; `DomainValidationException` → 400; `DataIntegrityViolationException` → 409; `Exception` → 500 (no stack trace in body). (GP-Rq-3)
- [x] 7.3 `health/ReadinessHealthIndicator.java` — checks Postgres, Redis PING, Kafka `AdminClient.listTopics()`. (GP-Rq-4)
- [x] 7.4 `config/SecurityConfig.java` — permit-all + `// TODO Phase-6: replace with JWT auth` marker. (GP-Rq-9)
- [x] 7.5 Micrometer counters: `trades_captured_total` (increment on success); `trade_validation_failures_total` tagged `{field}` (increment per rejected field). (GP-Rq-8)

## 8. Integration test (Req 5.4 / GP-Rq-12)
- [x] 8.1 `TradeCaptureIntegrationTest` (Testcontainers Postgres + Redis + Kafka): POST valid trade → assert 201 + DB row at `CAPTURED` + `TRADE_CAPTURED` event on `fxops.trade.events` with correct `tradeId`. (Req 5.4)
- [x] 8.2 Integration test: replay same `X-Idempotency-Key` → assert 200 + same `tradeId` returned + no second DB row + no second Kafka event. (Req 5.3)
- [x] 8.3 All test fixtures use `FX-` prefixed IDs, fictional counterparty/book names. (GP-Rq-14)

## 9. Final verification
- [x] 9.1 `mvn -pl Middleware/trade-ingest-service verify` — all tests green, zero failures.
- [x] 9.2 Update `MASTER-PLAN.md`: mark `02-trade-ingest-service` design ✅ tasks ✅.

---
**Completion:** 35 / 35 tasks. Update this line as tasks are ticked.
