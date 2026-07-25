# Tasks — DLQ Management (Cross-Cutting Strategy)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific files, and is independently verifiable.
> This is a **living checklist** — mark `[x]` as each task is completed and verified. Tags in
> parentheses trace to design sections (§) and requirements (Req / GP-Rq).
>
> **Nature:** this is a shared **config/strategy** deliverable — a shared library
> (`fxops-dlq-support`), one operational reader (`DLQConsumer`), a gated-replay tool, and
> topic provisioning — not a new bounded-context service.

## 0. Shared library scaffold
- [ ] 0.1 Create Maven module `Middleware/fxops-dlq-support` (shared library) with `<parent>` → `Middleware/pom.xml`; add to parent `<modules>`. (§1.2)
- [ ] 0.2 Add dependencies: `shared-domain-contracts`, Spring Kafka, Micrometer, Testcontainers (Kafka) for tests. *(products resolved from technology-stack)* (§1.1)
- [ ] 0.3 Create operational module `Middleware/dlq-consumer` (`@SpringBootApplication`, `spring.application.name=dlq-consumer`) depending on `fxops-dlq-support`, Spring Data JPA + PostgreSQL, Actuator/Micrometer. (§1.2, §6)
- [ ] 0.4 Context-load test asserting the `dlq-consumer` context starts. **Verify:** `mvn -pl Middleware/dlq-consumer test` green.

## 1. Externalized retry/DLQ configuration (Req 2.5; GP-Rq-11)
- [ ] 1.1 `config/DLQProperties` (`@ConfigurationProperties("fxops.dlq")`): per-topic `maxRetries`, `initialBackoffMs`, `maxBackoffMs`, `multiplier`; `poison.classify-as-poison` list. (§3)
- [ ] 1.2 Default `application.yml` fragment binding `fxops.trade.events` (5 / 1s / 30s) and `fxops.risk.requests` (3 / 2s / 30s). (Req 2.2/2.3)
- [ ] 1.3 Unit test: properties bind for both topics; values externalized (tunable without code change). **Verify:** unit test green. (Req 2.5)

## 2. Shared retry + route-to-DLQ handler (Req 2; consistent with GP-Rq-10)
- [ ] 2.1 `errorhandler/PoisonClassifier.isPoison(Throwable)` — walks cause chain against `classify-as-poison`; unknown → transient. (§4, §9)
- [ ] 2.2 `errorhandler/DlqErrorHandlerFactory` producing a `DefaultErrorHandler` with `ExponentialBackOff` from `DLQProperties`, poison → skip-retries-and-dead-letter-now. (§3, Req 2.4)
- [ ] 2.3 Non-blocking backoff wiring (staged delay-topic / `@RetryableTopic`-style) so backoff does not block the partition. (§3, §11, Req 2.6)
- [ ] 2.4 Unit tests: poison exceptions → true, transient → false, unknown → false; budget/backoff resolved per topic. **Verify:** unit tests green. (Req 2)

## 3. Quarantine header enrichment + verbatim payload (Req 3)
- [ ] 3.1 `headers/QuarantineHeaderEnricher` attaching all 8 `dlq.*` headers; `dlq.failure.reason` truncated to 500 chars; `dlq.failure.timestamp` ISO-8601. (§5, Req 3.2)
- [ ] 3.2 Configure the `DeadLetterPublishingRecoverer` to preserve the original payload **verbatim** and set `PartitionKey` = original tradeId key. (Req 3.3/3.4)
- [ ] 3.3 WARN dead-letter log with `CorrelationId`, `tradeId`, `dlq.origin.topic`, `dlq.origin.offset`, `dlq.failure.reason`; copy envelope `correlationId` → `dlq.correlation.id`. (Req 3.5; GP-Rq-2)
- [ ] 3.4 Unit test: all 8 headers present, reason truncated at 500, correlationId copied. **Verify:** unit test green. (Req 3)

## 4. DLQ topic provisioning (Req 1)
- [ ] 4.1 Topic-provisioning descriptor defining `fxops.trade.events.DLT` and `fxops.risk.requests.DLT`, partitions matching origin, retention ≥ 14 days, cleanup=delete (no compaction). (§2, Req 1.2/1.4/1.5)
- [ ] 4.2 Guard/checklist item: a new consumer's origin `.DLT` is provisioned in the same PR; `.DLT` topics get no error handler (one level deep). (Req 1.3/1.4)
- [ ] 4.3 Verify: `.DLT` topics created on a Testcontainers Kafka with the expected partition count and retention. **Verify:** provisioning test green.

## 5. Poison quarantine projection (Req 4)
- [ ] 5.1 Schema migration for `dlq_poison_quarantine` (columns per §4; unique `(origin_topic, origin_offset)`). **Verify:** migration applies on Testcontainers Postgres. (Req 4.2)
- [ ] 5.2 `quarantine/QuarantineEntity` + `QuarantineRepository`; idempotent upsert on natural key; `resolution_state` default `OPEN`. (§4, Req 4.5)
- [ ] 5.3 Query support by `origin_topic`, `trade_id`, `poison_flag`, arrival date range. (Req 4.4)
- [ ] 5.4 Resolution operations `discard` / `manual-reprocess` flip `resolution_state` (never delete); write `resolved_by` + `resolution_approval`. (Req 4.3/4.5)

## 6. DLQConsumer — monitoring & metrics (Req 5; GP-Rq-8)
- [ ] 6.1 `consumer/DlqReader` `@KafkaListener` on all `.DLT` topics; INFO log with `CorrelationId`, `tradeId`, `dlq.poison.flag`, `dlq.failure.reason`. (§6, Req 5.5)
- [ ] 6.2 Metric `dlq_messages_total{origin_topic,poison,reason_signature}` (signature = exception class, message stripped). (§6, GP-Rq-8)
- [ ] 6.3 Metric `dlq_depth{topic}` from consumer-group lag on each `.DLT`. (Req 5.1)
- [ ] 6.4 Metric `dlq_poison_message_count{origin_topic}` = unresolved (`OPEN`) poison entries. (Req 5.2)
- [ ] 6.5 On poison record: upsert quarantine projection (idempotent) and update poison gauge. (§4, §6)

## 7. Gated replay (Req 6)
- [ ] 7.1 `replay/GatedReplayTool` (action-gate tool): reads a non-poison dead-letter record, requires valid `ReplayApproval`, rejects if `dlq.poison.flag=true`. (§7, Req 6.2/6.3)
- [ ] 7.2 Re-publish to origin topic with **new** `eventId`, original `correlationId`, added `dlq.replay.approval` header. (Req 6.4)
- [ ] 7.3 Schema migration `dlq_replay_log` (unique `(origin_topic, origin_offset)`); replay idempotent — same offset replayed twice → one message. (Req 6.5)
- [ ] 7.4 Audit every replay to `dlq_replay_log`: synthetic operator id, approval ref, origin topic/offset, tradeId, timestamp. (Req 6.6)

## 8. Triage-agent envelope (§7)
- [ ] 8.1 `triage/TriageEnvelope` DTO + mapper producing the compact envelope (headers + signature + quarantine state, **no raw payload**). (§7)
- [ ] 8.2 Emit the envelope from `DlqReader` for downstream triage-agent consumption; assert payload body is excluded. (§7, GP-Rq-13 boundary)

## 9. Tests — force-a-poison end-to-end (Req; GP-Rq-12)
- [ ] 9.1 Integration (Testcontainers **Kafka + Postgres**): force a schema-invalid `FX-` record on `fxops.trade.events` → lands on `fxops.trade.events.DLT` with `dlq.poison.flag=true`, full header set, payload byte-identical, `dlq_poison_quarantine` row `OPEN`. (§10, Req 2.4/3/4)
- [ ] 9.2 Integration: transient failure stub failing N times → exactly `maxRetries` attempts, exponential spacing, dead-letter with `poison=false`. (§10, Req 2.2)
- [ ] 9.3 Integration: replay non-poison record twice (same offset) → exactly one re-published message, new `eventId`, original `correlationId`, `dlq.replay.approval` present; poison replay rejected. (§10, Req 6.2/6.5)
- [ ] 9.4 Assert `dlq_messages_total` and `dlq_poison_message_count{origin_topic}` present after the poison test. (§6, Req 5.2)
- [ ] 9.5 All fixtures use synthetic `FX-` ids and fictional `fxops.*` topics. (GP-Rq-14)

## 10. Verification & tracking
- [ ] 10.1 `mvn -pl Middleware/fxops-dlq-support,Middleware/dlq-consumer verify` — build + all tests green.
- [ ] 10.2 Update `MASTER-PLAN.md`: mark `03-events/04-dlq-management` design+tasks+code complete.
- [ ] 10.3 Commit via `scripts/commit-specs.sh` / normal commit.

---
**Completion:** 0 / 41 tasks. Update this line as tasks are ticked.
