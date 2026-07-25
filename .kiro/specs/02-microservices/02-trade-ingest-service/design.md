# Design Document — Trade Capture (Bounded Context)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). Realizes
> `requirements.md` for the Trade Capture bounded context. Technology Roles
> resolve to concrete products per `01-initial-setup/01-technology-stack`.
> Inherited golden-path NFRs get concrete implementations here.

## 1. Overview

`trade-ingest-service` is the **only** context that mints a new `tradeId` and
persists a trade at status `CAPTURED`. It exposes one state-changing REST
endpoint, validates the incoming trade request, writes to PostgreSQL, publishes
`TradeCaptured` to Kafka (atomic), and caches the idempotency marker in Redis.

**Technology Role → concrete binding:**

| Technology Role | Concrete product | Use in this service |
|---|---|---|
| `SERVICE_LANGUAGE` / `SERVICE_FRAMEWORK` | Java 21 / Spring Boot 3.4.x | service runtime |
| `SERVICE_BUILD_TOOL` | Maven 3.9.x | build |
| `RELATIONAL_STORE` | PostgreSQL 16.x | captured trade record of record |
| `CACHE` | Redis 7.x | exactly-once idempotency markers |
| `EVENT_STREAM` | Apache Kafka 3.x | `TradeCaptured` publication |
| `SERIALIZATION` | Jackson (ISO-8601, numeric decimals) | REST + Kafka (de)serialization |
| `BEAN_VALIDATION` | Jakarta Bean Validation + Hibernate Validator | request validation |
| `INTEGRATION_TEST_HARNESS` | Testcontainers | Postgres + Redis + Kafka integration tests |

---

## 2. Module and package structure

Maven module `Middleware/trade-ingest-service`, package root
`com.fxtradeops.tradeingest`:

```
config/
  SecurityConfig.java         — permit-all placeholder (GP-Rq-9)
  KafkaProducerConfig.java    — producer bean, topic, serializer config
  RedisConfig.java            — Lettuce connection factory
  ObservabilityConfig.java    — Micrometer / OTel export
api/
  TradeIngestController.java  — POST /api/v1/trades
  dto/
    TradeRequest.java         — inbound DTO + Bean Validation annotations
    TradeCaptureResponse.java — outbound confirmation (tradeId, correlationId, status)
application/
  TradeCaptureService.java    — orchestrates: validate → id-gen → persist → publish
  TradeIdGenerator.java       — sequence-based FX-NNNNNN generator
  IdempotencyService.java     — Redis check + mark
domain/
  BusinessDayValidator.java   — pure function: is tradeDate within 5 business days?
persistence/
  CapturedTradeEntity.java    — JPA entity for `captured_trades` table
  CapturedTradeRepository.java — Spring Data JPA
web/
  CorrelationIdFilter.java    — GP-Rq-2
  GlobalExceptionHandler.java — GP-Rq-3 (400/409/500 envelopes)
health/
  ReadinessHealthIndicator.java — GP-Rq-4 (Postgres + Redis + Kafka)
```

---

## 3. API design (Req 1 / GP-Rq-1)

### POST `/api/v1/trades`

**Request headers:**
- `X-Idempotency-Key` (UUID, required — the exactly-once capture key)
- `X-Correlation-Id` (UUID, optional — adopted if present, generated if absent)

**Request body (`TradeRequest`):**

```json
{
  "currencyPair":     { "baseCurrency": "USD", "quoteCurrency": "INR", "pairCode": "USD/INR" },
  "notionalAmount":   1500000.00,
  "notionalCurrency": "USD",
  "direction":        "BUY",
  "tradeDate":        "2025-07-24",
  "valueDate":        "2025-07-26",
  "counterpartyId":   "CP-AURORA-001",
  "tradingBookId":    "BOOK-APAC-001",
  "regionCode":       "APAC"
}
```

**Success response `201 Created`:**

```json
{
  "tradeId":       "FX-000001",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "status":        "CAPTURED"
}
```

**Validation failure `400 Bad Request`** (GP-Rq-3 `ErrorEnvelope`):

```json
{
  "status": 400,
  "timestamp": "2025-07-24T09:00:00Z",
  "errors": [
    { "field": "notionalAmount", "message": "must be strictly positive" },
    { "field": "valueDate",      "message": "must be strictly after tradeDate" }
  ]
}
```

**Idempotency replay `200 OK`** — returns original `TradeCaptureResponse` with the already-minted `tradeId`.

---

## 4. Validation design (Req 2)

Two-layer validation:

**Layer 1 — Bean Validation (`@Valid` on controller parameter):**
- `@NotNull`, `@NotBlank`, `@Positive`, `@Size(min=3,max=3)` on `TradeRequest` fields
- `@Pattern(regexp="^[A-Z]{3}/[A-Z]{3}$")` on `pairCode`
- Fires before `TradeCaptureService` is called; produces `400` via `GlobalExceptionHandler`

**Layer 2 — Domain rules in `TradeCaptureService`:**
- `tradeDate` ≤ today − 5 business days → `BusinessDayValidator.isWithinWindow(tradeDate, today)`
- `valueDate` not strictly after `tradeDate` → direct comparison
- All domain violations collected and thrown as a single `DomainValidationException` → `GlobalExceptionHandler` maps to `400`

`BusinessDayValidator` is a pure function (no DB, no network): it uses
`java.time.DayOfWeek` to skip weekends and a hard-coded set of fictional
holiday dates (configurable). **No call to `business-calendar-service` at
capture time** — that service provides booking-date classification, not
5-business-day capture window enforcement.

---

## 5. Trade ID generation (Req 1.3)

```java
// TradeIdGenerator — sequence backed by a PostgreSQL sequence
// CREATE SEQUENCE trade_id_seq START 1 INCREMENT 1;
public String next() {
    long seq = jdbcTemplate.queryForObject(
        "SELECT nextval('trade_id_seq')", Long.class);
    return String.format("FX-%06d", seq);
}
```

- PostgreSQL sequence guarantees uniqueness and monotonicity under concurrency
- No application-level locking needed
- Max value: `FX-999999` (1M trades); the sequence can be extended trivially

---

## 6. Persistence design (Req 1, GP-Rq-6)

**Table: `captured_trades`**

| column | type | notes |
|---|---|---|
| `id` | `bigserial` PK | surrogate key |
| `trade_id` | `varchar(10)` UNIQUE NOT NULL | business key `FX-######` |
| `correlation_id` | `varchar(36)` NOT NULL | |
| `currency_pair_code` | `varchar(7)` NOT NULL | e.g. `USD/INR` |
| `base_currency` | `char(3)` NOT NULL | |
| `quote_currency` | `char(3)` NOT NULL | |
| `notional_amount` | `numeric(19,4)` NOT NULL | fixed-scale |
| `notional_currency` | `char(3)` NOT NULL | |
| `direction` | `varchar(4)` NOT NULL | `BUY`/`SELL` |
| `trade_date` | `date` NOT NULL | |
| `value_date` | `date` NOT NULL | |
| `counterparty_id` | `varchar(50)` NOT NULL | |
| `trading_book_id` | `varchar(50)` NOT NULL | |
| `region_code` | `varchar(8)` NOT NULL | |
| `status` | `varchar(16)` NOT NULL | default `CAPTURED` |
| `created_at` | `timestamptz` NOT NULL | |
| `version` | `bigint` NOT NULL DEFAULT 0 | optimistic lock (GP-Rq-6) |

Schema managed via Flyway migration `V1__create_captured_trades.sql`.

---

## 7. Idempotency design (Req 3 / GP-Rq-5)

```
POST /api/v1/trades  with X-Idempotency-Key: <uuid>
  │
  ├─ Redis: GET idempotency:{key}
  │     HIT  → return cached TradeCaptureResponse (200)
  │     MISS → proceed to capture
  │
  ├─ Validate + generate tradeId
  ├─ BEGIN TX (Postgres)
  │   INSERT INTO captured_trades
  ├─ Kafka.send(TradeCaptured) — transactional producer
  ├─ COMMIT TX
  │
  └─ Redis: SET idempotency:{key} = {tradeId,status} TTL 24h
           (set AFTER commit — marker only after success)
```

Redis key: `idempotency:{X-Idempotency-Key}` — value is the serialized
`TradeCaptureResponse` JSON (compact). TTL 24 hours (configurable).

**Failure recovery:** if the service crashes between COMMIT and Redis SET, the
next replay will miss the Redis key and re-execute. PostgreSQL's UNIQUE
constraint on `trade_id` will not fire because a new `tradeId` is generated.
To handle this edge case, `IdempotencyService` checks the DB for an existing
row with the same `X-Idempotency-Key` (stored in a separate `idempotency_keys`
table) **before** generating a new `tradeId`. This guarantees strict
exactly-once even across crash-recovery.

**`idempotency_keys` table:**

| column | type | notes |
|---|---|---|
| `idempotency_key` | `varchar(36)` PK | the UUID from header |
| `trade_id` | `varchar(10)` NOT NULL | the minted `tradeId` |
| `created_at` | `timestamptz` NOT NULL | |

---

## 8. Atomic publish design (Req 4 / GP-Rq-7)

Kafka transactional producer: `spring.kafka.producer.transaction-id-prefix=trade-ingest-`.

Within `TradeCaptureService`:
```java
@Transactional          // Postgres transaction
kafkaTemplate.executeInTransaction(ops -> {
    capturedTradeRepository.save(entity);     // Postgres write
    idempotencyKeyRepository.save(keyEntity); // Postgres write
    ops.send("fxops.trade.events", tradeId, tradeCapturedEvent); // Kafka
    return null;
});
// Redis SET idempotency marker only AFTER transaction commits
```

This uses Spring's `ChainedTransactionManager` pattern for Postgres + Kafka
transactional producer. The Kafka transaction guarantees the event is committed
only if the Postgres transaction commits.

---

## 9. Application of golden-path NFRs (concrete)

| Golden-path | Concrete implementation |
|---|---|
| GP-Rq-2 correlation ID | `CorrelationIdFilter` reads `X-Correlation-Id` header, stores in MDC `correlationId`, propagates to Kafka event envelope |
| GP-Rq-3 error envelope | `GlobalExceptionHandler` — `MethodArgumentNotValidException` → 400; `DomainValidationException` → 400; `DataIntegrityViolationException` (duplicate) → 409; all others → 500 |
| GP-Rq-4 readiness | `ReadinessHealthIndicator` checks Postgres (`DataSource.getConnection()`), Redis (`PING`), Kafka (`AdminClient.listTopics()`) |
| GP-Rq-5 idempotency | §7 |
| GP-Rq-6 optimistic lock | `@Version` on `CapturedTradeEntity` |
| GP-Rq-7 atomicity | §8 transactional producer |
| GP-Rq-8 observability | `trades_captured_total` counter (Micrometer); `trade_validation_failures_total{field}` counter; OTel auto-instrumentation |
| GP-Rq-9 security | `SecurityConfig` permit-all + `// TODO Phase-6: replace with JWT auth` marker |
| GP-Rq-11 config | `application.yml` profiles: `local`, `aws`, `azure`; all endpoints/credentials via env vars |
| GP-Rq-12 testing | §10 |

---

## 10. Testing strategy (Req 5 / GP-Rq-12)

- **Unit** (`JUnit 5`): `TradeCaptureServiceTest` — each validation rule independently; `TradeIdGeneratorTest` — format assertion; `BusinessDayValidatorTest` — weekend + holiday edge cases.
- **Web-layer** (`MockMvc`): `TradeIngestControllerTest` — 201 happy path; each validation rule returns 400 with correct field; idempotency replay returns 200.
- **Integration** (`Testcontainers` Postgres + Redis + Kafka): `TradeCaptureIntegrationTest` — full flow: POST valid trade → assert 201 + DB row at CAPTURED + Kafka `TRADE_CAPTURED` event on `fxops.trade.events`; duplicate `X-Idempotency-Key` → assert same `tradeId` returned, no second DB row, no second Kafka event.
- All fixtures use `SyntheticData`: `FX-` prefixed IDs, fictional counterparty/book names.

---

## 11. Requirement → design traceability

| Requirement | Satisfied by |
|---|---|
| Req 1 — capture endpoint | §3, §5, §6, §8 |
| Req 2 — validation rules | §4 |
| Req 3 — exactly-once | §7 |
| Req 4 — TradeCaptured event | §8 |
| Req 5 — acceptance scenarios | §10 |
| Inherited GP-Rq-* | §9 |
