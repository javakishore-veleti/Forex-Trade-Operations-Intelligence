# Tasks — Domain Events Model (Schema Catalogue)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific files, and is independently verifiable.
> This is a **living checklist** — mark `[x]` as each task is completed and verified. Tags in
> parentheses trace to design sections (§) and requirements (Req / GP-Rq / topic-design Req).
>
> **This spec ships no service** — deliverables are **registered schema files**, their
> **compatibility/round-trip tests**, and the **catalogue document**. Schema files live under
> `DevOps/Local/EVENT_STREAM/schemas/`; round-trip contract tests live in
> `Middleware/shared-domain-contracts` (they validate the kernel projection of these schemas).
> All fixtures use synthetic `FX-` ids and fictional names (GP-Rq-14).

## 0. Envelope & catalogue foundation
- [ ] 0.1 Author the shared `EventEnvelope` Avro schema `DevOps/Local/EVENT_STREAM/schemas/common/event-envelope.avsc` with fields per §2 (`eventId`, `eventType`, `schemaVersion`, `correlationId`, `tradeId`, `sequenceNumber`, `sourceService`, `occurredAt`, `publishedAt`) — every field required/non-null. (Req 1.1/1.6, §2)
- [ ] 0.2 Define shared payload sub-records reused across events: `currency-pair.avsc`, `contributing-factor.avsc`, `amended-field.avsc`, `region-summary.avsc`, mirroring the shared-kernel `CurrencyPair`/`ContributingFactor` shapes. (§5)
- [ ] 0.3 Establish the JSON-Schema mirror convention: for each `.avsc`, a generated `*.schema.json` under `schemas/json/` for documentation/non-JVM consumers; Avro remains canonical. (§1 ADR-1)
- [ ] 0.4 **Verify:** every `.avsc` in `schemas/` parses without error (schema-lint / registry static validation). 

## 1. Trade lifecycle event schemas → `TradeEventType` (§3.1, Req 2/3)
- [ ] 1.1 `schemas/trade-events/trade-captured.avsc` — full captured-trade payload (§3.1 row 1); assert it carries the complete field set (Req 2.3).
- [ ] 1.2 `trade-validated.avsc`, `trade-enriched.avsc` (rows 2–3); stage-unknown fields omitted, not null-filled. (Req 2.2)
- [ ] 1.3 `trade-booked.avsc`, `trade-allocated.avsc`, `trade-confirmed.avsc`, `trade-settled.avsc` (rows 6–9); monetary fields JSON-number fixed-scale, temporals ISO-8601. (Req 2.4/2.5)
- [ ] 1.4 `trade-amended.avsc` — `amendedFields[]{fieldName,previousValue,newValue}`, only changed fields present. (Req 3.1/3.3)
- [ ] 1.5 `trade-cancelled.avsc` — terminal-event contract noted; `trade-failed.avsc` with `failedStage`. (Req 3.2/3.4)
- [ ] 1.6 `event-replayed.avsc`, `processing-paused.avsc`, `processing-resumed.avsc` (rows 13–15). (§3.1)
- [ ] 1.7 Compose the discriminated `fxops.trade.events-value` union/record over rows 1–3, 6–15 keyed by `eventType`. (§3.1, ADR-3)
- [ ] 1.8 **Verify:** a synthetic instance of each row validates against its schema and against the composed subject.

## 2. Risk event schemas (§3.1 rows 4–5, §3.2, Req 4)
- [ ] 2.1 `schemas/risk-events/risk-calculation-requested.avsc` (`fxops.risk.requests-value`) — payload per §3.1 row 4, incl. `calculationRequestId`. (Req 4.1)
- [ ] 2.2 `risk-calculation-completed.avsc` (`fxops.risk.results-value`) — `contributingFactors[]`, `rulesFired[]`, `calculationRequestId` matching the request. (Req 4.2/4.4)
- [ ] 2.3 `risk-calculation-failed.avsc` (`fxops.risk.results-value`) — `tradeId`, `calculationRequestId`, `failureReason`, `failedAt`. (Req 4.3)
- [ ] 2.4 **Verify:** completed-event fixture's `contributingFactors` sum to `riskAmount` within tolerance. (Req 4.5, §8)

## 3. EOD status event schemas (§3.3, Req 5)
- [ ] 3.1 `schemas/eod-events/regional-close-started|ready|blocked|closed.avsc` (`fxops.eod.status-value`), each keyed by `regionCode`. (Req 5.1/5.2)
- [ ] 3.2 `global-consolidation-completed.avsc` — `regionSummary[]{regionCode,status}`; document exactly-once-per-`globalBusinessDate` semantics. (Req 5.1/5.4)
- [ ] 3.3 Assert `globalBusinessDate` is an ISO-8601 date (`YYYY-MM-DD`), not wall-clock. (Req 5.3)
- [ ] 3.4 **Verify:** each EOD instance validates; partition-key doc records `regionCode`. (Req 5.2, §7)

## 4. Replay / reprocessing schemas (§3.4, Req 6)
- [ ] 4.1 `schemas/replay/replay-requested.avsc` (`fxops.trade.events-value`) — incl. non-null `approvalReference`; document producer restriction to `state-reconciliation-service`/gated workflow and medium-risk consumer handling. (Req 6.1/6.2/6.3/6.4)
- [ ] 4.2 **Verify:** a `REPLAY_REQUESTED` fixture with blank `approvalReference` is flagged malformed/unauthorized in the contract test. (Req 6.2)

## 5. Enum-extension reconciliation (§3.5, ADR-4)
- [ ] 5.1 Author `docs/adr/ADR-domain-events-eventtype-extension.md`: record that `RISK_CALCULATION_FAILED`, the EOD status family, and `REPLAY_REQUESTED` are not in the current 15-constant `TradeEventType`, and specify the backward-compatible resolution (extend `TradeEventType` **or** add sibling `OperationalEventType`). (§3.5, ADR-4)
- [ ] 5.2 Cross-reference the ADR from `02-microservices/01-shared-domain-contracts` so the kernel owns the enum change. (§5)

## 6. Schema registry subjects & compatibility policy (§4, topic-design Req 5)
- [ ] 6.1 `DevOps/Local/EVENT_STREAM/schemas/registry-subjects.yaml` mapping each subject (`fxops.trade.events-value`, `fxops.risk.requests-value`, `fxops.risk.results-value`, `fxops.eod.status-value`) to its schema file and `CompatibilityMode: BACKWARD`. (§4, §7, topic-design Req 5.1/5.2)
- [ ] 6.2 Registration step (idempotent) that registers every subject on local `EVENT_STREAM` startup, invoked from the `DevOps/Local/EVENT_STREAM/` init flow. (topic-design Req 5.1/6.3)
- [ ] 6.3 Document the breaking-change procedure (ADR first → widen to FULL/NONE for migration window → restore BACKWARD) in the registry-subjects file header. (§4, topic-design Req 5.3)
- [ ] 6.4 **Verify:** registration is a no-op on re-run; all four subjects present at `BACKWARD`.

## 7. Compatibility & round-trip contract tests (§8; GP-Rq-12)
- [ ] 7.1 `Middleware/shared-domain-contracts/src/test/java/.../event/schema/SchemaWellFormednessTest` — every `.avsc` parses and registers against a Testcontainers registry. (§8)
- [ ] 7.2 `BackwardCompatibilityTest` — current schema is BACKWARD-compatible with the prior registered version; a representative breaking change (drop a required field) is **rejected**. (§4, §8)
- [ ] 7.3 `TradeEventRoundTripTest` — for each §3.1 event, build a synthetic `TradeEvent` from kernel types, serialize via `DomainObjectMapper`, validate against the registered schema, deserialize, assert envelope + payload equality. (§5, §8, kernel §7)
- [ ] 7.4 `EnvelopeInvariantTest` — null/blank required envelope field → malformed (Req 1.6); stage-unknown payload field omitted not null (Req 2.2).
- [ ] 7.5 Property tests (`PROPERTY_TEST`): random synthetic `tradeId`/amount/instant tuples round-trip; monetary fields keep fixed scale. (Req 2.4, §8)
- [ ] 7.6 All fixtures use synthetic `FX-` ids + fictional service/rule names. (Req 7.4, GP-Rq-14)
- [ ] 7.7 **Verify:** `mvn -pl Middleware/shared-domain-contracts test` green.

## 8. Schema catalogue document (Req 7)
- [ ] 8.1 Author `.kiro/specs/03-events/02-domain-events-model/schema-catalogue.md` listing **every** event type (Reqs 2–6): its topic, `SchemaSubject`, `PartitionKey`, and all payload fields. (Req 7.1)
- [ ] 8.2 Give **every field** a business-meaning description, not just a type. (Req 7.2)
- [ ] 8.3 Add the maintenance rule: a new event type updates `schema-catalogue.md` in the same PR as its producing service's spec. (Req 7.3)
- [ ] 8.4 Ensure all example values use `FX-` ids / fictional names. (Req 7.4)
- [ ] 8.5 **Verify:** catalogue rows reconcile 1:1 with the schema files authored in §1–§4 (no orphan schema, no undocumented event).

## 9. Verification & tracking
- [ ] 9.1 Full contract-test pass: `mvn -pl Middleware/shared-domain-contracts test` + registry compatibility check green.
- [ ] 9.2 Update `MASTER-PLAN.md`: mark `03-events/02-domain-events-model` design + tasks complete (Design Done / Tasks Done for phase 03-events).
- [ ] 9.3 Commit via `scripts/commit-specs.sh` / normal commit.

---
**Completion:** 0 / 43 tasks. Update this line as tasks are ticked.
