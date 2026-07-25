# Tasks — Event Sequence Processor (Bounded Context)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific files, and is independently verifiable.
> This is a **living checklist** — mark `[x]` as each task is completed and verified. Tags in
> parentheses trace to design sections (§) and requirements (Req / GP-Rq).

## 0. Module scaffold
- [ ] 0.1 Create Maven module `Middleware/event-sequence-processor` with `<parent>` → `Middleware/pom.xml`; add to parent `<modules>`. (§2)
- [ ] 0.2 Add dependencies: `shared-domain-contracts`, Spring Web, Spring Kafka + Kafka Streams, Actuator, Micrometer/OTel, Spring Data JPA + PostgreSQL driver (optional projection), Testcontainers (test), `TopologyTestDriver` (test). *(products resolved from technology-stack)* (§1)
- [ ] 0.3 `EventSequenceProcessorApplication` (`@SpringBootApplication`, `@EnableKafkaStreams`) + `application.yml` (`spring.application.name=event-sequence-processor`, `processing.guarantee=exactly_once_v2`). (§2, §5)
- [ ] 0.4 `config/SequenceProperties` (`@ConfigurationProperties`) for `GracePeriod`, retention window, threshold, topic names — nothing hard-coded. (§7, GP-Rq-11, Req 6.4)
- [ ] 0.5 Context-load test asserting the application context starts and the Streams topology builds. **Verify:** `mvn -pl Middleware/event-sequence-processor test` green.

## 1. Domain — sequence model & shared state machine (Req 1)
- [ ] 1.1 `domain/SequenceFact`, `ObservedEvent`, `SequenceViolation`, `ViolationType` (enum: `MISSING_EVENT`, `MISSING_EVENT_RESOLVED`, `DUPLICATE_EVENT`, `CONFLICTING_REPLAY`, `OUT_OF_ORDER_EVENT`, `ORPHAN_CHILD_EVENT`, `POST_CLOSE_EVENT`). (§3, §4)
- [ ] 1.2 `domain/SequenceStateMachine` mirroring the Trade Lifecycle `PERMITTED` transition table (shared-kernel source); `expectedNextFrom(status)` and `statusFor(eventType)` as pure functions. (§3, Req 1.3)
- [ ] 1.3 Unit tests: expected-next derivation for each status; event-type → status induction. **Verify:** unit tests green. (Req 1.2/1.3)

## 2. State store & SerDes (Req 1.5, 6.2)
- [ ] 2.1 `state/SequenceFactSerde` (Jackson) for the `SequenceFact` value type. (§5)
- [ ] 2.2 `state/SequenceFactStore` — persistent key-value store `sequence-fact-store` keyed by `tradeId`, changelog `fxops.internal.sequence-processor.facts`, compacted. (§5, Req 1.5)
- [ ] 2.3 Verify changelog-backed restore semantics documented and store registered with the topology. **Verify:** store builder wired in a topology-build test.

## 3. Topology — stream wiring (Req 1)
- [ ] 3.1 `topology/SequenceTopology`: consume `fxops.trade.events`, re-key by `tradeId`, attach `SequenceFactTransformer` bound to `sequence-fact-store`, sink violations to `fxops.sequence.anomalies`. (§3)
- [ ] 3.2 `topology/SequenceFactTransformer.transform`: correlation-id → MDC, load fact, run `DetectionEngine`, mutate fact (append observed, recompute expected-next, advance status only in-order), put fact, forward new-violation envelopes. (§3, §4, GP-Rq-2)
- [ ] 3.3 `config/KafkaStreamsConfig`: `exactly_once_v2`, default SerDes, RocksDB state store, standby replicas for Interactive Queries. (§5, §6, Req 6.1)

## 4. Detection rules (Req 2, 3, 4)
- [ ] 4.1 `detection/OutOfOrderRule` → `OUT_OF_ORDER_EVENT`; records in observation list, does not advance `currentStatus`. (§4, Req 4.1/4.3)
- [ ] 4.2 `detection/DuplicateEventRule` → `DUPLICATE_EVENT` (same `eventId`, identical `payloadHash`); stream continues. (§4, Req 3.1/3.3)
- [ ] 4.3 `detection/ConflictingReplayRule` → `CONFLICTING_REPLAY` (same `eventId`, differing `payloadHash`); both payloads captured. (§4, Req 3.4)
- [ ] 4.4 `detection/MissingEventRule` → `MISSING_EVENT`; record prerequisite as missing; emitted-once per (tradeId, missing type); grace-suppressed. (§4, Req 2.1/2.3, 5.5)
- [ ] 4.5 `detection/OrphanChildRule` → `ORPHAN_CHILD_EVENT` (non-initiating event, no existing fact); `detection/PostCloseRule` → `POST_CLOSE_EVENT` (event after `complete`). (§4)
- [ ] 4.6 `detection/DetectionEngine` composes rules as pure functions of `(fact, event)`; unit tests: every `ViolationType` positive + negative case. (§4, §11, Req 7 acceptance)

## 5. Grace period & terminal expiry (Req 2.4, 1.4)
- [ ] 5.1 `punctuation/GracePeriodPunctuator`: scan store for elapsed grace windows; on late arrival within grace, clear `MissingEvent` and emit `MISSING_EVENT_RESOLVED`. (§4, Req 2.4, 5.5)
- [ ] 5.2 Terminal-event handling: mark `SequenceFact.complete`; retention punctuator expires the entry after the configured window. (§5, Req 1.4)

## 6. Anomaly envelope emission (Req 5)
- [ ] 6.1 `anomaly/AnomalyEnvelope` (standard `EventEnvelope` metadata + violation-specific fields per §4) + `AnomalyEnvelopeFactory`; nulls elided at serialization. (§4, Req 5.2/5.4)
- [ ] 6.2 Publish to `fxops.sequence.anomalies` with `tradeId` as partition key, within one processing interval, never buffered; only on violation/threshold. (§4, Req 5.1/5.3)

## 7. Query API — read models (Req — service query; GP-Rq-1)
- [ ] 7.1 `api/SequenceQueryController` `GET /api/v1/sequence/{tradeId}` and `/violations` via Interactive Queries; DTOs; 404 unknown; 503 when key not local. (§6)
- [ ] 7.2 Endpoints read-only / side-effect free. (§6, GP-Rq-1.4)

## 8. Golden-path realizations (inherited NFRs → concrete) (§7)
- [ ] 8.1 `web/CorrelationIdFilter` + transformer MDC copy + `%X{correlationId}` log pattern; correlation id set on every emitted envelope. (GP-Rq-2)
- [ ] 8.2 `web/GlobalExceptionHandler` (`@RestControllerAdvice`) → 400/404/409/503/500 envelopes, no stack traces in body. (GP-Rq-3)
- [ ] 8.3 `health/StreamsReadinessHealthIndicator`: `DOWN` if Streams not `RUNNING`/store not restored / Kafka unreachable. (GP-Rq-4, Req 6.3)
- [ ] 8.4 `config/SecurityConfig` permit-all + standard Phase-6 auth TODO marker. (GP-Rq-9)
- [ ] 8.5 Business metrics `sequence_violations_total{type}`, `sequence_facts_active`, `anomaly_envelopes_published_total{type}`, `grace_period_resolutions_total` via Micrometer; trace context across `EVENT_STREAM` boundary. (GP-Rq-8)
- [ ] 8.6 Confirm no model invocation and no high-volume stream crosses the agent boundary — only compact envelopes. (GP-Rq-13, §1)

## 9. Tests — domain scenarios (Req 6.5; GP-Rq-12)
- [ ] 9.1 `TopologyTestDriver` tests: drive synthetic records; assert `SequenceFact` mutations + forwarded envelopes without a broker. (§11)
- [ ] 9.2 Web-layer tests: query endpoints incl. 404 + anomaly-visible views. (GP-Rq-12.1)
- [ ] 9.3 Integration (Testcontainers Kafka): inject out-of-order stream → `OUT_OF_ORDER_EVENT` + status unchanged. (Req 4)
- [ ] 9.4 Integration: duplicate `eventId` → `DUPLICATE_EVENT`; conflicting replay → `CONFLICTING_REPLAY` with both payloads. (Req 3)
- [ ] 9.5 Integration: sequence gap then late arrival within grace → `MISSING_EVENT` suppressed then `MISSING_EVENT_RESOLVED`. (Req 2)
- [ ] 9.6 Integration: restart mid-stream → store restored from changelog, no duplicate envelopes. (Req 6.1/6.2)
- [ ] 9.7 Property-based tests: observed-event multiset preserved under permutation; in-order advancement idempotent. (GP-Rq-12.3, §12)
- [ ] 9.8 All fixtures use synthetic `FX-` ids and fictional `fxops.*` topics. (GP-Rq-14)

## 10. Verification & tracking
- [ ] 10.1 `mvn -pl Middleware/event-sequence-processor verify` — build + all tests green.
- [ ] 10.2 Update `MASTER-PLAN.md`: mark `03-event-sequence-processor` design+tasks+code complete.
- [ ] 10.3 Commit via `scripts/commit-specs.sh` / normal commit.

---
**Completion:** 0 / 43 tasks. Update this line as tasks are ticked.
