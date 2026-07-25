# Tasks — State Reconciliation (Bounded Context)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific files, and is independently verifiable.
> This is a **living checklist** — mark `[x]` as each task is completed and verified. Tags in
> parentheses trace to design sections (§) and requirements (Req / GP-Rq).

## 0. Module scaffold
- [ ] 0.1 Create Maven module `Middleware/state-reconciliation-service` with `<parent>` → `Middleware/pom.xml`; add to parent `<modules>`. (§2)
- [ ] 0.2 Add dependencies: `shared-domain-contracts`, Spring Web, Spring Data JPA + PostgreSQL driver, Spring Data MongoDB, Spring Data Redis, Spring Kafka, Actuator, Micrometer/OTel, Testcontainers (test). *(products resolved from technology-stack)* (§1)
- [ ] 0.3 `StateReconciliationApplication` (`@SpringBootApplication`) + `application.yml` (`spring.application.name=state-reconciliation-service`; source endpoints + profiles externalized). (§2, GP-Rq-11)
- [ ] 0.4 Context-load test asserting the application context starts. **Verify:** `mvn -pl Middleware/state-reconciliation-service test` green.

## 1. Domain — canonical expected-state derivation (Req 2)
- [ ] 1.1 `domain/canonical/LifecycleTransitions` — the immutable `PERMITTED` table + terminal statuses, a deterministic mirror of the lifecycle state machine (shared/duplicated). (§4)
- [ ] 1.2 `domain/canonical/CanonicalStateDeriver.derive(orderedHistory)` as a pure fold over `targetFor(eventType)` + `PERMITTED`; stable `(occurredAt, sequence)` ordering. (§4, Req 2.1)
- [ ] 1.3 `DerivationResult` with `COMPLETE` vs `INCOMPLETE_HISTORY` (furthest supported state on a gap / no `CAPTURED`). (§4, Req 2.3)
- [ ] 1.4 Unit tests: full path derives correctly; a missing required event ⇒ `INCOMPLETE_HISTORY` at furthest state; same history ×2 ⇒ identical state; no majority vote / no LLM path. **Verify:** unit tests green. (Req 2.4, 8.3, GP-Rq-13)

## 2. Source adapters — read-only observed state (Req 1)
- [ ] 2.1 `source/ObservedStateSource` port + `ObservedState` record (`source, status, sourceTimestamp, available`); `SourceId` enum. (§3)
- [ ] 2.2 `source/relational/RelationalStateSource` — read-only `@Transactional(readOnly=true)` read of `trade_current_state.status/updated_at` (Postgres). (§3, Req 1.4)
- [ ] 2.3 `source/document/DocumentStateSource` — latest `trade_lifecycle_audit` `toStatus/occurredAt` (Mongo, read-only). (§3)
- [ ] 2.4 `source/cache/CacheStateSource` — `GET state:{tradeId}` (Redis, read-only). (§3)
- [ ] 2.5 `source/stream/EventStreamStateSource` — latest event for `tradeId` → `targetFor(eventType)` (Kafka, read-only, no offset advance). (§3, §7)
- [ ] 2.6 `source/analytics/AnalyticsStateSource` — optional Databricks source, `@ConditionalOnProperty`. (§3, Req 1.5, GP-Rq-11)
- [ ] 2.7 Each adapter catches infra failure → logs with correlation id → returns `UNAVAILABLE` (never throws to caller). (§3, §12, Req 1.3)
- [ ] 2.8 `application/EventHistoryReader` — ordered event history from `DOCUMENT_STORE` (input to §4 derivation), read-only. (§7, Req 2.1)

## 3. Domain — divergence detection & classification (Req 3)
- [ ] 3.1 `domain/divergence/DivergenceDetector` — flag a `Divergence` for every available source whose observed status ≠ canonical. (§5, Req 3.1)
- [ ] 3.2 `domain/divergence/DivergenceClassifier` — `STALE` / `AHEAD` / `CONFLICTING` by position on the canonical path. (§5, Req 3.2)
- [ ] 3.3 `domain/divergence/StaleSourceResolver` — deterministic most-likely-stale from source timestamps + fixed precedence. (§5, Req 3.3)
- [ ] 3.4 `CONSISTENT` outcome when all available sources agree (zero divergences); `UNAVAILABLE` sources excluded from agreement but retained in `states`. (§5, Req 3.4)
- [ ] 3.5 Unit tests for each classification + most-likely-stale + consistent case. **Verify:** unit tests green. (Req 8.1)

## 4. Domain — invariants & permitted actions (Req 4)
- [ ] 4.1 `domain/invariant/Invariant` + `InvariantCatalogue` (stable codes) + `InvariantEvaluator` → `violatedInvariants` (code + description). (§6, Req 4.1)
- [ ] 4.2 `domain/action/PermittedAction` — the **fixed enumerated catalogue** (`REFRESH_CACHE`, `REPLAY_EVENT`, `RESYNC_DOCUMENT_STORE`, `RESYNC_RELATIONAL_STORE`, `OPEN_RECONCILIATION_CASE`, `NO_ACTION`). (§6, Req 4.3)
- [ ] 4.3 `domain/action/PermittedActionPolicy` — deterministic divergence → permitted-action mapping; catalogue-bounded, no free-form, no model expansion; never executes. (§6, Req 4.2/4.4/4.5)
- [ ] 4.4 Unit tests: representative divergences → expected actions; assert every emitted action ∈ catalogue; no execution side effect. (Req 8.2)

## 5. Domain — business-impact classification (Req 6)
- [ ] 5.1 `domain/impact/BusinessImpact` ordinal enum (`NONE<LOW<MEDIUM<HIGH<CRITICAL`) + `BusinessImpactClassifier` deterministic from divergence nature + canonical stage; references risk context for exposure, computes no money. (§9, Req 6)
- [ ] 5.2 Unit tests: settlement-stage conflict ⇒ `CRITICAL`; early-stage cache lag ⇒ `LOW`; consistent ⇒ `NONE`. (Req 6)

## 6. Application — reconciliation orchestration & result envelope (Req 5)
- [ ] 6.1 `application/ReconciliationService.reconcile(tradeId)` — read all sources → derive canonical → detect+classify → invariants → permitted actions → impact → assemble `ReconciliationResult`. (§8, §11)
- [ ] 6.2 `domain/model/ReconciliationResult` envelope: `states` (incl. `UNAVAILABLE`), `expectedState`, `derivation`, `divergences`, `mostLikelyStaleSource`, `violatedInvariants`, `permittedActions`, `businessImpact` — PRD-compatible shape. (§8, Req 5.2/5.3)
- [ ] 6.3 `application/SweepService` — batch/on-demand sweep over a set of `tradeId`s (or filter) → one result per trade. (§8, Req 5.4)

## 7. API — read-only query (Req 5; GP-Rq-1)
- [ ] 7.1 `api/ReconciliationQueryController` `GET /api/v1/reconciliation/{tradeId}` + DTOs; 404 when unknown to every source. (§8)
- [ ] 7.2 `POST /api/v1/reconciliation/sweep` (`SweepRequest{tradeIds|filter}`) → result array; endpoints read-only, side-effect free. (§8, Req 5.4, GP-Rq-1.4)

## 8. Golden-path realizations (inherited NFRs → concrete) (§10)
- [ ] 8.1 `web/CorrelationIdFilter` + MDC copy + `%X{correlationId}` log pattern. (GP-Rq-2)
- [ ] 8.2 `web/GlobalExceptionHandler` (`@RestControllerAdvice`) → 400/404/500 envelopes, no stack traces in body. (GP-Rq-3)
- [ ] 8.3 `health/DegradedReadinessHealthIndicator` — **ready** with exactly one of {Postgres, Mongo, Redis, Kafka} down (source `UNAVAILABLE`); **503 not-ready** only when ≥2 down. **(overrides GP-Rq-4)** (§10, Req 7)
- [ ] 8.4 `config/SecurityConfig` permit-all + standard Phase-6 auth TODO marker. (GP-Rq-9)
- [ ] 8.5 Business metrics `reconciliations_total{status}`, `divergences_total{source,classification}`, `source_unavailable_total{source}` via Micrometer. (GP-Rq-8)

## 9. Tests — domain scenarios (Req 8; GP-Rq-12)
- [ ] 9.1 Web-layer tests: `GET /{tradeId}` incl. 404 + sweep; envelope shape matches Req 5 incl. an `UNAVAILABLE` source. (Req 8.4, GP-Rq-12.1)
- [ ] 9.2 Integration (Testcontainers Postgres+Mongo+Redis+Kafka): all sources = canonical → `CONSISTENT`, zero divergences. (Req 8.1)
- [ ] 9.3 Integration — canonical divergence scenario: `RELATIONAL`=`BOOKED`, `DOCUMENT`=`RISK_CALCULATED`, `CACHE`=`PENDING`, latest event=`TRADE_CANCELLED` → assert `expectedState`, per-source classification, `violatedInvariants`, and every action ∈ catalogue. (Req 8.2)
- [ ] 9.4 Integration — determinism: reconcile same trade twice on identical data → identical `expectedState`. (Req 8.3)
- [ ] 9.5 Integration — degraded readiness: one source down ⇒ ready + `UNAVAILABLE`; two down ⇒ 503 not-ready. (Req 7)
- [ ] 9.6 All fixtures use synthetic `FX-` ids. (GP-Rq-14)

## 10. Verification & tracking
- [ ] 10.1 `mvn -pl Middleware/state-reconciliation-service verify` — build + all tests green.
- [ ] 10.2 Update `MASTER-PLAN.md`: mark `07-state-reconciliation-service` design+tasks+code complete.
- [ ] 10.3 Commit via `scripts/commit-specs.sh` / normal commit.

---
**Completion:** 0 / 46 tasks. Update this line as tasks are ticked.
