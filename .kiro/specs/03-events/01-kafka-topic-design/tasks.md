# Tasks — Kafka Topic Design (Platform Event-Stream Topology)

> **Stage 3 of 3** (`requirements.md → design.md → tasks.md`). Derived from `design.md`. Execute
> top-to-bottom; each task is atomic, maps to specific files, and is independently verifiable.
> This is a **living checklist** — mark `[x]` as each task is completed and verified. Tags in
> parentheses trace to design sections (§) and requirements (Req / GP-Rq).
>
> **Infra spec — no service module.** All artifacts live under `DevOps/Local/EVENT_STREAM/`;
> there is no Maven module and no `mvn` step. Verification is topic-config assertion against a
> local `CONTAINER_RUNTIME` broker.

## 0. Scaffold
- [ ] 0.1 Create the `DevOps/Local/EVENT_STREAM/` tree: `topics/`, `schema-registry/subjects/`, `scripts/`. (§11)
- [ ] 0.2 Add `topics/topic-registry.schema.json` describing the registry entry shape (name, partitions, replicationFactor, minInsyncReplicas, cleanupPolicy, retentionMs, partitionKey). (§8, §11) **Verify:** the JSON Schema itself parses (`jq . topics/topic-registry.schema.json`).

## 1. Topic-registry file — single source of truth (Req 1, 6)
- [ ] 1.1 Author `topics/topic-registry.yml` with one entry per platform topic in §2: `fxops.trade.events`, `fxops.risk.requests`, `fxops.risk.results`, `fxops.eod.status`, `fxops.sequence.anomalies`, `fxops.dlq.trade-events`, `fxops.dlq.risk-requests`. (Req 1.5, §2)
- [ ] 1.2 For each entry record `partitionKey` (`tradeId`, except `fxops.eod.status` → `regionCode`) as documentation. (Req 2.1/2.2, §3)
- [ ] 1.3 Every name lowercase, hyphen-separated, `fxops.` prefixed; DLQ as `fxops.dlq.<origin-short-name>`. (Req 1.2/1.4) **Verify:** no name matches `[A-Z_ ]`.

## 2. Partition & replication config (Req 2, 3)
- [ ] 2.1 Set `partitions: 6` on every domain event topic; add a comment that reducing requires an ADR. (Req 2.3, §3)
- [ ] 2.2 Set `replicationFactor: 3` and `minInsyncReplicas: 2` for the multi-broker profile on every domain topic. (Req 3.1/3.4)
- [ ] 2.3 Provide a single-broker local override (`replicationFactor: 1`, `minInsyncReplicas: 1`) each marked `# local-only`. (Req 3.2)
- [ ] 2.4 DLQ topics inherit their origin's partition count, RF, ISR, and key. (Req 2.4, 3.3) **Verify:** DLQ entries equal their origin on those four fields.

## 3. Retention & cleanup config (Req 4)
- [ ] 3.1 Set `cleanupPolicy: delete` + `retentionMs`: `fxops.trade.events`=30d, `fxops.risk.requests`/`fxops.risk.results`=7d, `fxops.eod.status`=90d, `fxops.sequence.anomalies`=30d. (Req 4.1–4.4)
- [ ] 3.2 DLQ topics: `cleanupPolicy: delete`, `retentionMs`=14d, and an explicit `# never compacted` note. (Req 4.5)
- [ ] 3.3 Document that `fxops.internal.*` Streams topics use `cleanupPolicy: compact` (managed by STREAM_PROCESSING, not created here). (Req 4.6, §4) **Verify:** no DLQ or domain topic has `cleanupPolicy: compact`.

## 4. Schema-registry config (Req 5)
- [ ] 4.1 Add `schema-registry/subjects/<TopicName>-value.json` (JSON Schema) for each domain topic; DLQ topics carry the envelope schema. (Req 5.1, §5)
- [ ] 4.2 Add `schema-registry/compatibility.config` setting the global default compatibility to `BACKWARD`. (Req 5.2)
- [ ] 4.3 Document the ADR-gated widening-to-FULL/NONE procedure for breaking changes as a header comment in `compatibility.config`. (Req 5.3, §5)
- [ ] 4.4 Add the schema-registry container + the producer-readiness note (registry unreachable ⇒ producer not ready) to the compose file. (Req 5.5, GP-Rq-4, §5) **Verify:** every domain topic has a matching `{TopicName}-value` subject file.

## 5. Compose + provisioning script (Req 6)
- [ ] 5.1 Author `docker-compose.event-stream.yml`: KRaft-mode broker(s) + schema-registry + an init step, all with pinned image tags (no `latest`). (§1, §11)
- [ ] 5.2 Author `scripts/provision-topics.sh` reading `topic-registry.yml` and creating every topic (partitions/RF/ISR/cleanup/retention) idempotently. (Req 6.1/6.3, §8)
- [ ] 5.3 Wire the init step to run `provision-topics.sh` on first startup so a clean environment is fully provisioned with no manual steps. (Req 6.3) **Verify:** `docker compose … up` then `kafka-topics --list` shows all 7 topics.

## 6. Consumer-group naming reference (§6)
- [ ] 6.1 Document the `fxops.<consuming-service>.<topic-short-name>` group-id convention (with the example table) in the registry README/header comment; groups themselves are created by consuming services, not here. (§6)

## 7. Validation (§9)
- [ ] 7.1 Author `scripts/validate-topics.sh` — **registry lint**: name pattern, no uppercase/underscore, domain partitions ≥ 6, DLQ retention ≥ 14d and not compacted. (Req 1.4, 2.3, 4.5, §9)
- [ ] 7.2 Extend it with **live-vs-registry** assertions: describe each provisioned topic and compare partitions/RF/ISR/cleanup/retention to the registry. (§9)
- [ ] 7.3 Add **schema-subject** assertions: each domain topic has a `{TopicName}-value` subject at `BACKWARD`. (Req 5.1/5.2, §9)
- [ ] 7.4 Add a **synthetic-data** assertion: every name is within the `fxops.*` namespace; no real identifiers. (Req 6.5, GP-Rq-14, §9) **Verify:** `scripts/validate-topics.sh` exits 0 against the provisioned local stack.

## 8. Verification & tracking
- [ ] 8.1 Full local run: `docker compose -f docker-compose.event-stream.yml up`, then `scripts/validate-topics.sh` green end-to-end. (§9)
- [ ] 8.2 Update `MASTER-PLAN.md`: mark `03-events/01-kafka-topic-design` design+tasks+config complete.
- [ ] 8.3 Commit via `scripts/commit-specs.sh` / normal commit.

---
**Completion:** 0 / 27 tasks. Update this line as tasks are ticked.
