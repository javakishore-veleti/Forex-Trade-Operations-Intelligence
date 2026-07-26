# Tasks — Risk Calculation (Bounded Context)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific files, and is independently verifiable.
> This is a **living checklist** — mark `[x]` as each task is completed and verified. Tags in
> parentheses trace to design sections (§) and requirements (Req / GP-Rq).

## 0. Module scaffold
- [x] 0.1 Create Maven module `Middleware/risk-calculation-service` with `<parent>` → `Middleware/pom.xml`; add to parent `<modules>`. (§2)
- [x] 0.2 Add dependencies: `shared-domain-contracts`, Spring Web, Spring Data JPA + PostgreSQL driver, Spring Kafka, Spring Data Redis, Drools (`kie-ci` / `drools-core` / `drools-mvel`), Actuator, Micrometer/OTel, Testcontainers + JUnit 5 + jqwik (test). *(products resolved from technology-stack)* (§1)
- [x] 0.3 `RiskCalculationApplication` (`@SpringBootApplication`) + `application.yml` (`spring.application.name=risk-calculation-service`, `risk.rules.package-path`). (§2)
- [x] 0.4 Context-load test asserting the application context starts. **Verify:** `mvn -pl Middleware/risk-calculation-service test` green.

## 1. Domain — enums & value types (Req 2, 3, 4)
- [x] 1.1 `domain/RiskLevel` enum (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`). (Req 2.4)
- [x] 1.2 `domain/ScopeType` enum (`REGION`, `BOOK`, `GLOBAL`, `COUNTERPARTY`). (§5)
- [x] 1.3 `domain/ContributingFactor` value type (`factorName`, `contributionAmount` BigDecimal, `currency`). (Req 4.1)
- [x] 1.4 `domain/RiskComputationResult` value type (`riskAmount`, `riskLevel`, `factors`, `rulesFired`, `ruleVersion`). (§4)
- [x] 1.5 Unit test: `RiskLevel` / `ScopeType` value semantics and factor immutability. **Verify:** unit tests green.

## 2. Domain — deterministic risk engine (Req 2, 3, 4)
- [x] 2.1 `domain/RiskFact` mutable Drools working-memory POJO (`riskAmount`, `rulesFired`, factor hints) built from the request. (§3, §4)
- [x] 2.2 `domain/RiskLevelClassifier.classify(riskAmount, regionCode)` — deterministic, configurable threshold → `RiskLevel`, pure function. (§4, Req 2.4)
- [x] 2.3 `domain/ContributingFactorCalculator.decompose(fact, riskAmount)` — always emits `CURRENCY_PAIR_VOLATILITY`, `NOTIONAL_EXPOSURE`, `REGIONAL_ADJUSTMENT`. (§4, Req 4.3)
- [x] 2.4 `domain/FallbackRuleDetector` — detects `"FALLBACK"` in the `rulesFired` list. (§3, Req 3.4)
- [x] 2.5 `domain/RiskEngine.compute(req)` — new `StatelessKieSession`, insert `RiskFact`, fire, set scale-4 `HALF_UP` `riskAmount`, decompose factors, assert factor sum within `ROUNDING_TOLERANCE` (else `RiskArithmeticException`), classify level. (§4, Req 2.1/2.2, 4.2)
- [x] 2.6 Unit tests: determinism (same input × 2 = same output); factors sum to `riskAmount`; fallback fires on uncovered pair. **Verify:** unit tests green. (Req 8.1/8.2/8.3)

## 3. Drools rules-engine integration (Req 3)
- [x] 3.1 Versioned DRL rule package on the classpath: specific currency-pair rules + `salience -1` catch-all `FALLBACK` rule; `// RULE_VERSION: RULES-7.14` marker in the `package` declaration. (§3, Req 3.1/3.4)
- [x] 3.2 `config/DroolsConfig` — `KieContainer` bean from `${risk.rules.package-path}` `Resource`; `buildAll()` at startup. (§3, Req 3.1)
- [x] 3.3 Rule-version extraction from the DRL `// RULE_VERSION:` comment, exposed for stamping onto `RiskResult.ruleVersion`. (§3, Req 3.3)
- [x] 3.4 Test: `KieContainer` builds with no errors; rule version parses; a `StatelessKieSession` fires the expected pair rule. **Verify:** unit tests green. (Req 3.1/3.2)

## 4. Persistence — relational stores (Req 5, 6, 7; GP-Rq-6)
- [x] 4.1 `persistence/RiskResultEntity` (`risk_results` per §5 table) with `@Version`, `rulesFired`/`contributingFactors` as JSON text. (Req 2.3, GP-Rq-6)
- [x] 4.2 `persistence/RiskAggregationEntity` (`risk_aggregations` per §5) with `@Version`; PK on (`scope_type`,`scope_id`). (Req 5.1, GP-Rq-6)
- [x] 4.3 `persistence/LimitEntity` (`risk_limits`) and `persistence/LimitBreachEntity` (`limit_breaches`, append-only). (Req 6.1/6.2)
- [x] 4.4 `persistence/EodSnapshotEntity` (`eod_risk_snapshots`) with `UNIQUE(scope_type, scope_id, business_date)`. (Req 7.2/7.3)
- [x] 4.5 `persistence/repositories/` Spring Data JPA repositories for all five entities. (§2)
- [x] 4.6 Flyway migration creating `risk_results`, `risk_aggregations`, `risk_limits`, `limit_breaches`, `eod_risk_snapshots`. **Verify:** migration applies on a Testcontainers Postgres. (§5)

## 5. Persistence — dedup cache (Req 1; GP-Rq-5)
- [x] 5.1 `application/DedupService` over Redis (`SET dedup:risk:{eventId}` + TTL); `seen(eventId)` / `mark(eventId)`. (§6, GP-Rq-5)
- [x] 5.2 `config/RedisConfig` — connection factory + template for dedup markers. (§2)

## 6. Application — aggregation, limits, EOD (Req 5, 6, 7)
- [x] 6.1 `application/AggregationService.apply(result)` — update `REGION` + `BOOK` + `GLOBAL` totals and `trade_count` atomically with the result. (§5, §6, Req 5.1/5.2/5.5)
- [x] 6.2 `application/AggregationService.reverseContribution(tradeId)` — deduct a superseded result so it is never double-counted. (§6, Req 5.3)
- [x] 6.3 `application/LimitCheckerService.check(result, aggregations)` — evaluate result + affected aggregations vs configured `Limit`s; record `LimitBreach` facts, no blocking action. (§5, §6, Req 6.1/6.2/6.4)
- [x] 6.4 `application/EodSnapshotService.snapshot(scope, businessDate)` — finalize aggregation total + per-book breakdown + trade count + rule version; idempotent overwrite per (scope, date). (§5, Req 7.1/7.2/7.3)
- [x] 6.5 Unit test: aggregation apply then `reverseContribution` nets to zero; EOD re-snapshot overwrites (no accumulation). **Verify:** unit tests green. (Req 8.5)

## 7. Application — calculation orchestration (Req 1, 5, 6, 7; GP-Rq-7)
- [x] 7.1 `application/RiskCalculationService.process(request)`: `RiskEngine.compute` → `@Transactional { save RiskResult; AggregationService.apply; LimitCheckerService.check; publish RISK_CALCULATION_COMPLETED }`. (§6, Req 7.4, GP-Rq-7)
- [x] 7.2 Unresolvable trade reference data → publish trade-failed event with reason code, no `RiskResult`. (Req 1.3)
- [x] 7.3 On-demand `calculate(request)` path idempotent by `requestId`; includes previous `riskAmount` on recalculation for delta. (Req 1.2, 2.5, GP-Rq-5)

## 8. Consumers — event ingestion (Req 1, 5; GP-Rq-7)
- [x] 8.1 `config/KafkaConsumerConfig` (consumer group, manual ack) + `config/KafkaProducerConfig` (transactional producer). (§2, §6)
- [x] 8.2 `consumer/RiskCalculationRequestedConsumer` `@KafkaListener("fxops.risk.requests")`: correlation-id → MDC → dedup check → `RiskCalculationService.process` → **ack after tx commit** → Redis `mark`. (§6, Req 1.1, GP-Rq-7)
- [x] 8.3 `consumer/TradeEventConsumer` `@KafkaListener("fxops.trade.events")` for `TRADE_CANCELLED` / `TRADE_AMENDED` → `AggregationService.reverseContribution(tradeId)`. (§6, Req 5.3)

## 9. API — read models + on-demand (Req 1.2, 5.4, 6.3)
- [x] 9.1 `api/RiskQueryController` + `api/dto/` — `GET /api/v1/risk/{tradeId}/result`, `GET .../result/{calculationId}` (404 if none). (§7, Req 5.4)
- [x] 9.2 `POST /api/v1/risk/calculate` — on-demand, body = `RiskCalculationRequest`, idempotent by `requestId`. (§7, Req 1.2)
- [x] 9.3 `GET /api/v1/risk/aggregation?scope&id` + `GET /api/v1/risk/limits/breaches?scope&id` — read-only. (§7, Req 5.4, 6.3)

## 10. Golden-path realizations (inherited NFRs → concrete) (§8)
- [x] 10.1 `web/CorrelationIdFilter` + consumer MDC copy from event envelope + `%X{correlationId}` log pattern. (GP-Rq-2)
- [x] 10.2 `web/GlobalExceptionHandler` (`@RestControllerAdvice`) → 400/404/409/500 envelopes, no stack traces in body. (GP-Rq-3)
- [x] 10.3 `health/ReadinessHealthIndicator` checks Postgres + Redis + Kafka AdminClient + Drools `kieContainer != null`. (GP-Rq-4)
- [x] 10.4 `config/SecurityConfig` permit-all + standard Phase-6 auth TODO marker. (GP-Rq-9)
- [x] 10.5 Business metrics `risk_calculations_total{region,risk_level}`, `risk_calculation_duration_seconds`, `fallback_rule_firings_total{pair}` via Micrometer. (GP-Rq-8)

## 11. Tests — domain scenarios (Req 8; GP-Rq-12)
- [x] 11.1 Web-layer tests: result GET (incl. 404), aggregation + breaches GET, POST on-demand calculate. (GP-Rq-12.1, Req 1.2)
- [x] 11.2 Integration (Testcontainers Postgres+Redis+Kafka+Drools classpath rules): consume `RISK_CALCULATION_REQUESTED` → `RiskResult` persisted + aggregations updated + `RISK_CALCULATION_COMPLETED` published. (Req 8.4)
- [x] 11.3 Integration: `TRADE_CANCELLED` trade → affected aggregation reduced, no double-count. (Req 8.5)
- [x] 11.4 Integration: duplicate `eventId` → dedup, single result, no double-count. (Req 8.4, GP-Rq-5)
- [x] 11.5 All fixtures use synthetic `FX-` ids and fictional rule identifiers. (GP-Rq-14)

## 12. Verification & tracking
- [x] 12.1 `mvn -pl Middleware/risk-calculation-service verify` — build + all tests green.
- [x] 12.2 Update `MASTER-PLAN.md`: mark `04-risk-calculation-service` design+tasks+code complete.
- [x] 12.3 Commit via `scripts/commit-specs.sh` / normal commit.

---
**Completion:** 54 / 54 tasks. Update this line as tasks are ticked.
