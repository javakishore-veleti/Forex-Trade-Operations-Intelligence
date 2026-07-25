# Tasks — OTel Kafka Tracing (Cross-Cutting Instrumentation)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific config/files, and is independently verifiable.
> This is a **living checklist** — mark `[x]` as each task is completed and verified. Tags in
> parentheses trace to design sections (§) and requirements (Req / GP-Rq).
>
> **Cross-cutting, no new service.** Tasks apply to the event-touching services already built in
> phases 02/03 and defer to `05-observability/01-otel-spring-boot` for span names, baggage keys,
> agent/collector wiring, and DLQ error tagging (do not restate them here).

## 0. Propagator & instrumentation config (Req 1, 2; §7)
- [ ] 0.1 In each event-touching service's `ObservabilityConfig`, set `OTEL_PROPAGATORS=tracecontext,baggage` (W3C) so `traceparent`/`tracestate`/`baggage` are written and read; assert no proprietary propagator. (§2, §7, Req 1.1/2.1)
- [ ] 0.2 Enable the OTel Kafka producer/consumer instrumentation on the `KafkaConfig` producer/consumer factories; confirm **no manual header-setting/reading code** exists in service logic. (§2, §9, Req 1.2/2.2)
- [ ] 0.3 Externalize the OTel exporter endpoint + sampler args in `application.yml` (env vars, never hard-coded); reuse the collector wiring owned by `05-observability/01`. (§7, GP-Rq-11)
- [ ] 0.4 **Verify:** start one service against a Testcontainers Kafka; produce a record and assert a `traceparent` header is present on the `ProducerRecord`.

## 1. Header propagation — produce side (Req 1; §2, §4)
- [ ] 1.1 Confirm the `ProducerSpan` injects `traceparent` (and `tracestate`/`baggage` when present) into `record.headers()` on every publish to `fxops.trade.events` / `fxops.sequence.anomalies`. (Req 1.1/1.2)
- [ ] 1.2 Assert the injected `traceparent` carries the **triggering** unit-of-work `traceId` (inbound HTTP or upstream consume), not a freshly generated one. (Req 1.4)
- [ ] 1.3 Set producer span attributes `messaging.destination.name` (topic) and `messaging.kafka.message.key` = `PartitionKey` (`tradeId`/`regionCode`). (Req 1.3, §4)
- [ ] 1.4 **Verify:** one unit of work publishing N records → same `traceId`, N distinct `ProducerSpan` `spanId`s. (Req 1.5)

## 2. Header propagation — consume side (Req 2; §2, §4)
- [ ] 2.1 Confirm consumers extract `traceparent` from each `ConsumerRecord` and open the `ConsumerSpan` under the extracted parent context. (Req 2.1/2.2)
- [ ] 2.2 Missing-header path: a record with no `traceparent` → consumer starts a **new root** `ConsumerSpan` and does **not** fail. (Req 2.3)
- [ ] 2.3 Set consumer span attributes `messaging.destination.name`, `messaging.kafka.destination.partition`, `messaging.kafka.message.offset`. (Req 2.4, §4)
- [ ] 2.4 Set `trade.id` on the `ConsumerSpan` read from the **event envelope `tradeId`** (authoritative) and re-establish `tradeId`/`correlationId` baggage from headers; mirror `correlationId` to MDC (defer to `05-observability/01` Req 4 / GP-Rq-2). (Req 2.5, §4)

## 3. Producer/consumer span linkage (Req 3; §3)
- [ ] 3.1 Synchronous listeners (e.g. `trade-lifecycle-service`): `ConsumerSpan` **parent = `ProducerSpan`** (parent/child). (Req 3.1)
- [ ] 3.2 Batch listeners: batch span **links** to each record's `ProducerSpan` (span links, not parent) to avoid a wide, misleading tree. (Req 3.2)
- [ ] 3.3 **Verify:** in a produce→consume test the `ConsumerSpan.traceId == ProducerSpan.traceId` and the parent/link edge is present per pattern. (Req 3.4)

## 4. Stream-processing coverage (Req 5; §5)
- [ ] 4.1 `event-sequence-processor`: create a `StreamProcessingSpan` per record consumed from `fxops.trade.events`, **linked** to the source `ProducerSpan` via the extracted `traceparent`. (Req 5.1, Req 3.3)
- [ ] 4.2 Add `trade.id` and (when detected) `violationType` as `StreamProcessingSpan` attributes. (Req 5.3)
- [ ] 4.3 Anomaly publish to `fxops.sequence.anomalies` → `ProducerSpan` that is a **child** of the `StreamProcessingSpan`. (Req 5.2)
- [ ] 4.4 **Verify:** trade event → detection → anomaly publish share one `traceId` end-to-end. (Req 5)

## 5. DLQ trace survival (Req 4; §5)
- [ ] 5.1 Dead-letter routing (e.g. `fxops.trade.events` → `fxops.trade.events.dlq`) emits a **child `Span` of the `ConsumerSpan`** named `{service-name} dead-letter {origin-topic}`. (Req 4.1)
- [ ] 5.2 Dead-letter span attributes: `dlq.origin.topic`, `dlq.origin.partition`, `dlq.origin.offset`, `dlq.failure.reason` (≤200 chars), `dlq.poison.flag`; status = `ERROR` (defer error-tagging convention to `05-observability/01` Req 5.4). (Req 4.2/4.3)
- [ ] 5.3 Copy the original record's `traceparent` onto the DLQ record headers so a later DLQ read/replay continues the same trace. (Req 4.4)
- [ ] 5.4 **Verify:** poison record → dead-letter span (ERROR, `dlq.*` attrs) exists AND the DLQ record's `traceparent` equals the original's. (Req 4)

## 6. Sampling for high-volume topics (§6)
- [ ] 6.1 Configure a **parent-based** sampler (`parent_based(root = trace_id_ratio(p))`) so consumers inherit the producer's sampled flag; the whole chain is kept/dropped together. (§6)
- [ ] 6.2 Externalize the sampler ratio (`OTEL_TRACES_SAMPLER_ARG`) per environment: low ratio for high-volume load envs, `1.0` locally. (§6, GP-Rq-11)
- [ ] 6.3 Ensure `traceparent` is still injected for **un-sampled** traces (sampled flag `00`) and that DLQ/error spans are recorded regardless of ratio. (§6, Req 4)
- [ ] 6.4 **Verify:** ratio `1.0` → trade fully sampled end-to-end; an un-sampled parent → the whole chain consistently un-sampled (no partial traces).

## 7. Tests — continuity assertions (Req 1–5; GP-Rq-12)
- [ ] 7.1 Unit: W3C propagator inject→extract round-trip over a header carrier — same `traceId`/`spanId` recovered, sampled flag preserved. (§8)
- [ ] 7.2 Integration (Testcontainers Kafka): produce→consume for `FX-000001` → assert **one continuous trace** (`ConsumerSpan` shares `ProducerSpan` `traceId`, correct parent). (Req 1/2/3.1, §8)
- [ ] 7.3 Integration: missing-`traceparent` record → new root consumer span, no failure. (Req 2.3)
- [ ] 7.4 Integration: DLQ trace survival — dead-letter child span + `traceparent` copied to DLQ headers. (Req 4)
- [ ] 7.5 Integration: stream chain — `StreamProcessingSpan` linked to producer + child anomaly `ProducerSpan` share the trade `traceId`. (Req 5)
- [ ] 7.6 All fixtures use synthetic `FX-` ids and fictional `fxops.*` topics. (GP-Rq-14)

## 8. Verification & tracking
- [ ] 8.1 Run the module test commands for every instrumented event-touching service — build + all tracing tests green. (GP-Rq-12.5)
- [ ] 8.2 Update `MASTER-PLAN.md`: mark `05-observability/02-otel-kafka-tracing` design+tasks complete (Design/Tasks columns for the 05-observability row).
- [ ] 8.3 Commit via `scripts/commit-specs.sh` / normal commit.

---
**Completion:** 0 / 36 tasks. Update this line as tasks are ticked.
