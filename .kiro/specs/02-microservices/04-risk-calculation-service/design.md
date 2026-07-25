# Design Document — Risk Calculation (Bounded Context)

> **Stage 2 of 3** (`requirements.md → design.md → tasks.md`). Realizes
> `requirements.md` for the Risk Calculation bounded context. Technology Roles
> resolve to concrete products per `01-initial-setup/01-technology-stack`.

## 1. Overview

`risk-calculation-service` is the **deterministic risk authority**. It consumes
`RISK_CALCULATION_REQUESTED` events from Kafka, evaluates currency-pair rules
through Drools, computes a fixed-scale `riskAmount`, maintains regional/book/global
aggregations in PostgreSQL, checks limits, and publishes
`RISK_CALCULATION_COMPLETED`. Every figure is reproducible; no model is involved.

**Technology Role → concrete binding:**

| Technology Role | Concrete product | Use in this service |
|---|---|---|
| `SERVICE_LANGUAGE` / `SERVICE_FRAMEWORK` | Java 21 / Spring Boot 3.4.x | service runtime |
| `RELATIONAL_STORE` | PostgreSQL 16.x | risk results, aggregations, limits, EOD snapshots |
| `CACHE` | Redis 7.x | idempotent event-consumption dedup markers |
| `EVENT_STREAM` | Apache Kafka 3.x | consume requests, publish completed/failed |
| `RULES_ENGINE` | Drools 9.x | versioned currency-pair rule evaluation |
| `SERIALIZATION` | Jackson | REST + Kafka (de)serialization |
| `INTEGRATION_TEST_HARNESS` | Testcontainers | Postgres + Redis + Kafka integration tests |

---

## 2. Module and package structure

Maven module `Middleware/risk-calculation-service`, package root
`com.fxtradeops.riskcalc`:

```
config/
  SecurityConfig.java
  KafkaConsumerConfig.java        — consumer group, manual ack
  KafkaProducerConfig.java        — transactional producer
  DroolsConfig.java               — KieContainer bean, rule package load
  RedisConfig.java
  ObservabilityConfig.java
domain/
  RiskEngine.java                 — orchestrates Drools evaluation + arithmetic
  RiskLevelClassifier.java        — deterministic threshold → RiskLevel
  ContributingFactorCalculator.java — factor decomposition
  FallbackRuleDetector.java       — detects fallback firing
application/
  RiskCalculationService.java     — consume → compute → persist → publish
  DedupService.java               — Redis event dedup
  AggregationService.java         — update regional/book/global totals
  LimitCheckerService.java        — evaluate limits, record breaches
  EodSnapshotService.java         — finalize EOD totals
persistence/
  RiskResultEntity.java           — `risk_results` table
  RiskAggregationEntity.java      — `risk_aggregations` table
  LimitEntity.java                — `risk_limits` table
  LimitBreachEntity.java          — `limit_breaches` table
  EodSnapshotEntity.java          — `eod_risk_snapshots` table
  repositories/                   — Spring Data JPA
consumer/
  RiskCalculationRequestedConsumer.java — @KafkaListener
  TradeEventConsumer.java         — listen for CANCELLED/AMENDED
api/
  RiskQueryController.java        — GET endpoints (on-demand calc + aggregation query)
  dto/                            — request/response DTOs
web/
  CorrelationIdFilter.java
  GlobalExceptionHandler.java
health/
  ReadinessHealthIndicator.java   — Postgres + Redis + Kafka + Drools
```

---

## 3. Drools integration design (Req 3)

Drools rule package loaded at startup from a versioned DRL file on the classpath
(or configurable file path):

```java
// DroolsConfig.java
@Bean
public KieContainer kieContainer(
        @Value("${risk.rules.package-path}") Resource drlResource) {
    KieServices ks = KieServices.Factory.get();
    KieFileSystem kfs = ks.newKieFileSystem();
    kfs.write(ResourceFactory.newInputStreamResource(drlResource.getInputStream()));
    ks.newKieBuilder(kfs).buildAll();
    return ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
}
```

**Rule version:** extracted from the DRL's `package` declaration comment
`// RULE_VERSION: RULES-7.14`; stored in `RiskResult.ruleVersion`.

**Fallback rule:** a DRL rule with `salience -1` and a catch-all condition fires
when no specific pair rule matches. `FallbackRuleDetector` detects it by checking
whether `"FALLBACK"` appears in the `rulesFired` list.

**Drools session lifecycle:** a new `StatelessKieSession` per calculation (thread
safe, no shared mutable state). Session inserts trade facts, fires rules, extracts
risk facts from working memory.

---

## 4. Risk computation design (Req 2, 4)

All arithmetic uses `BigDecimal` with scale 4 and `HALF_UP` rounding.

```java
// RiskEngine.java
public RiskComputationResult compute(RiskCalculationRequest req) {
    StatelessKieSession session = kieContainer.newStatelessKieSession();
    RiskFact fact = new RiskFact(req); // mutable Drools working-memory object
    session.execute(Collections.singletonList(fact));

    BigDecimal riskAmount = fact.getRiskAmount()
            .setScale(4, RoundingMode.HALF_UP);

    List<ContributingFactor> factors =
        contributingFactorCalculator.decompose(fact, riskAmount);

    // verify factors sum within tolerance
    BigDecimal factorSum = factors.stream()
            .map(ContributingFactor::contributionAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (factorSum.subtract(riskAmount).abs()
            .compareTo(ROUNDING_TOLERANCE) > 0) {
        throw new RiskArithmeticException("factor sum mismatch");
    }

    RiskLevel level = riskLevelClassifier.classify(riskAmount, req.regionCode());
    return new RiskComputationResult(riskAmount, level, factors,
            fact.getRulesFired(), ruleVersion);
}
```

`RiskFact` is a POJO inserted into Drools working memory; rules set
`riskAmount`, `rulesFired`, and factor hints on it.

**Contributing factors** (Req 4.3): always include at minimum:
- `CURRENCY_PAIR_VOLATILITY` — pair-specific volatility factor from the rule
- `NOTIONAL_EXPOSURE` — notional × base-currency factor
- `REGIONAL_ADJUSTMENT` — region/book adjustment from fired rules (0 if none)

---

## 5. Persistence design (Req 5, 6, 7)

**`risk_results`** — one row per calculation:

| column | type | notes |
|---|---|---|
| `calculation_id` | `varchar(36)` PK | UUID |
| `trade_id` | `varchar(10)` NOT NULL | FK business key |
| `correlation_id` | `varchar(36)` | |
| `risk_amount` | `numeric(19,4)` | |
| `risk_currency` | `char(3)` | |
| `region_code` | `varchar(8)` | |
| `trading_book_id` | `varchar(50)` | |
| `calculated_at` | `timestamptz` | |
| `rule_version` | `varchar(20)` | |
| `risk_level` | `varchar(8)` | |
| `rules_fired` | `text` | JSON array |
| `contributing_factors` | `text` | JSON array |
| `version` | `bigint` DEFAULT 0 | optimistic lock |

**`risk_aggregations`** — one row per scope key:

| column | type | notes |
|---|---|---|
| `scope_type` | `varchar(8)` | `REGION` / `BOOK` / `GLOBAL` |
| `scope_id` | `varchar(50)` | regionCode or bookId or `GLOBAL` |
| `total_risk_amount` | `numeric(19,4)` | |
| `risk_currency` | `char(3)` | |
| `trade_count` | `int` | |
| `last_updated_at` | `timestamptz` | |
| `version` | `bigint` | optimistic lock |

**`risk_limits`** — configured limits:

| column | type | notes |
|---|---|---|
| `limit_id` | `varchar(36)` PK | |
| `scope_type` | `varchar(12)` | `REGION`/`BOOK`/`COUNTERPARTY` |
| `scope_id` | `varchar(50)` | |
| `limit_amount` | `numeric(19,4)` | |
| `currency` | `char(3)` | |

**`limit_breaches`** — append-only breach facts:

| column | type | notes |
|---|---|---|
| `breach_id` | `varchar(36)` PK | |
| `calculation_id` | `varchar(36)` | |
| `scope_type` | `varchar(12)` | |
| `scope_id` | `varchar(50)` | |
| `limit_amount` | `numeric(19,4)` | |
| `observed_amount` | `numeric(19,4)` | |
| `detected_at` | `timestamptz` | |

**`eod_risk_snapshots`** — EOD totals:

| column | type | notes |
|---|---|---|
| `snapshot_id` | `varchar(36)` PK | |
| `scope_type` | `varchar(8)` | |
| `scope_id` | `varchar(50)` | |
| `business_date` | `date` NOT NULL | |
| `total_risk_amount` | `numeric(19,4)` | |
| `trade_count` | `int` | |
| `rule_version` | `varchar(20)` | |
| `snapshotted_at` | `timestamptz` | |
| `UNIQUE(scope_type, scope_id, business_date)` | | idempotent by date |

All schemas managed via Flyway.

---

## 6. Event consumption and atomic publish (Req 1, 7 / GP-Rq-7)

Consumer: `@KafkaListener(topics="fxops.risk.requests")`, manual ack,
dedup via Redis `SET dedup:risk:{eventId}` with TTL.

Per-event flow:
```
dedup check (Redis)
  HIT  → ack, skip
  MISS → RiskCalculationService.process(request)
          → RiskEngine.compute()
          → @Transactional {
              save RiskResult
              updateAggregations (region + book + global)
              checkLimits → save LimitBreach if breached
              kafkaTemplate.send("fxops.risk.results", RISK_CALCULATION_COMPLETED)
            }
          → ack offset after tx commit
          → Redis SET dedup marker
```

`TradeEventConsumer` listens on `fxops.trade.events` for `TRADE_CANCELLED` and
`TRADE_AMENDED` — on receipt calls `AggregationService.reverseContribution(tradeId)`
to deduct the superseded result from aggregations.

---

## 7. REST API — on-demand + query (Req 1.2, 5.4, 6.3)

```
GET /api/v1/risk/{tradeId}/result      → latest RiskResult for trade (404 if none)
GET /api/v1/risk/{tradeId}/result/{calculationId} → specific calculation
POST /api/v1/risk/calculate            → on-demand: body = RiskCalculationRequest
GET /api/v1/risk/aggregation?scope=REGION&id=APAC → current aggregation
GET /api/v1/risk/limits/breaches?scope=REGION&id=APAC → breach facts
```

All read endpoints are side-effect free (GP-Rq-1.4). POST `/calculate` is
idempotent via `requestId` in the body.

---

## 8. Application of golden-path NFRs (concrete)

| Golden-path | Concrete implementation |
|---|---|
| GP-Rq-2 correlation ID | `CorrelationIdFilter` + consumer MDC copy from event envelope |
| GP-Rq-3 error envelope | `GlobalExceptionHandler` — validation 400, not-found 404, conflict 409 |
| GP-Rq-4 readiness | checks Postgres, Redis, Kafka AdminClient, Drools `kieContainer != null` |
| GP-Rq-5 idempotency | Redis dedup on `eventId`; on-demand idempotency by `requestId` |
| GP-Rq-6 optimistic lock | `@Version` on `RiskResultEntity`, `RiskAggregationEntity` |
| GP-Rq-7 atomicity | transactional Kafka producer; result + aggregation + breach + event in one tx |
| GP-Rq-8 observability | `risk_calculations_total{region,risk_level}`, `risk_calculation_duration_seconds`, `fallback_rule_firings_total{pair}` |
| GP-Rq-9 security | permit-all + Phase-6 auth TODO |
| GP-Rq-13 determinism | no LLM; BigDecimal arithmetic; Drools is deterministic; same input → same output |

---

## 9. Testing strategy (Req 8)

- **Unit**: `RiskEngineTest` — determinism: same input × 2 = same output; `ContributingFactorCalculatorTest` — factors sum to riskAmount; `FallbackRuleDetectorTest` — uncovered pair triggers fallback.
- **Web-layer**: `RiskQueryControllerTest` — GET endpoints; POST on-demand.
- **Integration** (Testcontainers Postgres + Redis + Kafka + Drools classpath rules): consume `RISK_CALCULATION_REQUESTED` → assert result persisted + aggregations updated + `RISK_CALCULATION_COMPLETED` published; `CANCELLED` trade → assert aggregation reduced; duplicate `eventId` → dedup, no double-count.

---

## 10. Requirement → design traceability

| Requirement | Satisfied by |
|---|---|
| Req 1 — request consumption + on-demand | §6, §7 |
| Req 2 — deterministic computation | §4 |
| Req 3 — rules engine + explainability | §3, §4 |
| Req 4 — contributing factors | §4 |
| Req 5 — aggregation | §5, §6 |
| Req 6 — limit checking | §5, §6 |
| Req 7 — EOD totals + completion event | §5, §6 |
| Req 8 — acceptance scenarios | §9 |
| Inherited GP-Rq-* | §8 |
