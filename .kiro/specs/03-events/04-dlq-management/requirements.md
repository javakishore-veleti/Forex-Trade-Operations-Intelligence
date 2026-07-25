# Requirements Document — DLQ Management

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. References topic definitions from
> `03-events/01-kafka-topic-design`. Contains no product names or versions.

## Introduction

This feature defines the **Dead-Letter Queue (DLQ) strategy** for the
platform's `EVENT_STREAM`. It covers DLQ topic naming (governed by
`03-events/01-kafka-topic-design`), per-topic retry policy, poison message
quarantine strategy, DLQ consumer monitoring, and the operational lifecycle
of a dead-lettered message from arrival through resolution.

DLQ management is a cross-cutting operational concern that applies to every
`Service_Module` that consumes from the `EVENT_STREAM`. Rather than each
service inventing its own retry and error-handling approach, this spec defines
the single platform standard that all consumers MUST follow. Deviation from
this standard requires an ADR.

All identifiers in examples use the synthetic `FX-` prefix. All service and
topic names are fictional.

---

## Glossary

- **DLQ**: Dead-Letter Queue — an `EVENT_STREAM` topic that receives messages
  a consumer could not process after exhausting its retry budget.
- **DLQTopic**: A topic following the naming pattern
  `fxops.dlq.{origin-topic-short-name}` (e.g. `fxops.dlq.trade-events`).
- **OriginTopic**: The topic from which a message was originally consumed
  before it was dead-lettered.
- **DeadLetteredMessage**: A message routed to a `DLQTopic` because it could
  not be processed successfully.
- **RetryPolicy**: The per-`OriginTopic` configuration that governs how many
  times a consumer retries a failed message, the backoff strategy, and the
  conditions under which it gives up and dead-letters.
- **PoisonMessage**: A `DeadLetteredMessage` whose payload is structurally
  invalid, schema-incompatible, or otherwise unprocessable regardless of
  retry; quarantined permanently until manually reviewed.
- **TransientFailure**: A processing failure caused by a temporary condition
  (e.g. a dependency unreachable) that may succeed on retry.
- **QuarantineHeader**: Metadata attached to a `DeadLetteredMessage` by the
  consuming service at the time of dead-lettering: `dlq.origin.topic`,
  `dlq.origin.partition`, `dlq.origin.offset`, `dlq.failure.reason`,
  `dlq.failure.count`, `dlq.failure.timestamp`, `dlq.poison.flag` (boolean),
  `dlq.correlation.id`.
- **DLQConsumer**: The monitoring/triage component that reads from `DLQTopic`s
  and surfaces metrics, alerts, and replay/quarantine decisions.
- **ReplayApproval**: A human-approved action (per the platform action-gate
  pattern) that authorizes replaying a `DeadLetteredMessage` back to its
  `OriginTopic`.

---

## Requirements

### Requirement 1: DLQ Topic Catalogue

**User Story:** As a platform operator, I want a defined DLQ topic for every
domain topic that has consumers, so that failed messages have a deterministic
landing place and are never silently dropped.

#### Acceptance Criteria

1. EVERY `OriginTopic` that has one or more consuming `Service_Module`s SHALL
   have a corresponding `DLQTopic` as defined in
   `03-events/01-kafka-topic-design` Requirement 1.
2. THE following `DLQTopic`s SHALL exist as part of the initial topic set:
   `fxops.dlq.trade-events` (origin: `fxops.trade.events`) and
   `fxops.dlq.risk-requests` (origin: `fxops.risk.requests`).
3. WHEN a new consuming `Service_Module` is added to the platform, its
   `OriginTopic`'s corresponding `DLQTopic` SHALL be provisioned in the same
   pull request that introduces the consumer.
4. `DLQTopic`s SHALL NOT themselves have DLQs; the DLQ chain is one level deep.
5. `DLQTopic` retention SHALL be a minimum of **14 days** (per
   `03-events/01-kafka-topic-design` Requirement 4) and SHALL NOT use log
   compaction.

---

### Requirement 2: Per-Topic Retry Policy

**User Story:** As a service developer, I want a defined retry policy per topic
so that transient failures are retried with bounded backoff before a message
is dead-lettered, while poison messages are identified quickly without
exhausting retry budgets on hopeless cases.

#### Acceptance Criteria

1. EVERY consuming `Service_Module` SHALL apply a `RetryPolicy` before
   dead-lettering a message; it SHALL NOT dead-letter on first failure.
2. THE `RetryPolicy` for `fxops.trade.events` consumers SHALL be:
   maximum **5 retries**, exponential backoff starting at **1 second**,
   maximum backoff of **30 seconds**.
3. THE `RetryPolicy` for `fxops.risk.requests` consumers SHALL be:
   maximum **3 retries**, exponential backoff starting at **2 seconds**,
   maximum backoff of **30 seconds**.
4. WHEN a retry attempt fails due to a schema validation error (indicating
   a `PoisonMessage`), THE consumer SHALL skip remaining retries, set
   `dlq.poison.flag = true` in the `QuarantineHeader`, and dead-letter
   immediately.
5. THE retry budget and backoff values SHALL be externalized as configuration
   (inherited GP-Rq-11) so they can be tuned without code changes.
6. BETWEEN retry attempts, THE consumer SHALL release the partition offset so
   that other messages in the partition continue to be processed; it SHALL NOT
   block the entire partition during backoff.

---

### Requirement 3: Dead-Lettering and Quarantine Headers

**User Story:** As a DLQ triage operator, I want every dead-lettered message
to carry rich headers explaining why it failed, so that I can diagnose and
remediate without needing to reproduce the failure.

#### Acceptance Criteria

1. WHEN a message is dead-lettered, THE consuming `Service_Module` SHALL
   attach the full `QuarantineHeader` set to the `DeadLetteredMessage` before
   publishing it to the `DLQTopic`.
2. THE `QuarantineHeader` SHALL include: `dlq.origin.topic` (the
   `OriginTopic`), `dlq.origin.partition` (integer), `dlq.origin.offset`
   (long), `dlq.failure.reason` (exception class name and message, truncated
   to 500 characters), `dlq.failure.count` (total attempts including retries),
   `dlq.failure.timestamp` (ISO-8601), `dlq.poison.flag` (boolean),
   `dlq.correlation.id` (the `CorrelationId` from the message envelope).
3. THE original message payload SHALL be preserved verbatim in the
   `DeadLetteredMessage` body; it SHALL NOT be modified, re-serialized, or
   truncated.
4. THE `PartitionKey` of the `DeadLetteredMessage` SHALL match the
   `PartitionKey` of the original message on the `OriginTopic` so that all
   DLQ messages for a given `tradeId` land on the same `DLQTopic` partition.
5. WHEN dead-lettering occurs, THE consuming service SHALL emit a WARN-level
   log entry containing the `CorrelationId`, `tradeId`, `dlq.origin.topic`,
   `dlq.origin.offset`, and `dlq.failure.reason`.

---

### Requirement 4: Poison Message Quarantine

**User Story:** As a platform operator, I want poison messages to be clearly
identified and quarantined so that they are not replayed automatically and
require explicit human review before any remediation.

#### Acceptance Criteria

1. A `DeadLetteredMessage` with `dlq.poison.flag = true` SHALL be classified
   as a `PoisonMessage` and SHALL NOT be eligible for automatic replay.
2. THE `DLQConsumer` SHALL maintain a **poison message quarantine list** — a
   projection of all `PoisonMessage`s by `OriginTopic`, `tradeId`,
   `dlq.origin.offset`, and `dlq.failure.reason` — persisted in the
   `RELATIONAL_STORE`.
3. A `PoisonMessage` SHALL only be resolved by one of two human-approved
   actions: **discard** (acknowledged unprocessable, logged to audit) or
   **manual reprocess** (payload corrected externally, re-submitted to the
   `OriginTopic` via a gated tool with `ReplayApproval`).
4. THE quarantine list SHALL be queryable by `OriginTopic`, `tradeId`,
   `dlq.poison.flag`, and arrival date range.
5. NO automatic process SHALL remove a `PoisonMessage` from the quarantine
   list; only an explicit human-approved resolution action SHALL do so.

---

### Requirement 5: DLQ Monitoring and Alerting

**User Story:** As a platform operator, I want DLQ depth and poison message
counts surfaced as metrics and alerts, so that a growing DLQ is detected
before it causes operational impact.

#### Acceptance Criteria

1. THE `DLQConsumer` SHALL expose a metric `dlq_depth{topic}` representing
   the current consumer-group lag on each `DLQTopic`; this metric SHALL be
   scraped by the `OBSERVABILITY_METRICS` role.
2. THE `DLQConsumer` SHALL expose a metric
   `dlq_poison_message_count{origin_topic}` representing the number of
   unresolved `PoisonMessage`s per `OriginTopic`.
3. AN alert SHALL be defined (in the `05-observability/03-otel-metrics-dashboards`
   spec) that fires when `dlq_depth{topic}` exceeds a configurable threshold
   for more than a configurable duration.
4. AN alert SHALL be defined that fires when any new `PoisonMessage` is
   quarantined, triggering immediate operator review.
5. THE `DLQConsumer` SHALL log every message it reads from a `DLQTopic` at
   INFO level, including `CorrelationId`, `tradeId`, `dlq.poison.flag`, and
   `dlq.failure.reason`.

---

### Requirement 6: Replay Workflow (Gated)

**User Story:** As a recovery operator, I want a controlled, human-approved
replay process for non-poison dead-lettered messages, so that transient
failures can be recovered without bypassing the action-gate pattern.

#### Acceptance Criteria

1. THE platform SHALL provide a replay capability that reads a
   `DeadLetteredMessage` from a `DLQTopic` and re-publishes it to its
   `OriginTopic`, subject to `ReplayApproval`.
2. BEFORE replaying, THE replay capability SHALL verify that the message is
   not a `PoisonMessage` (`dlq.poison.flag = false`); if it is, replay SHALL
   be rejected with a clear error.
3. THE replay action SHALL be gated by a `ReplayApproval` reference (the
   human-approval token from the HITL gate); a replay without a valid
   `ReplayApproval` SHALL be rejected.
4. WHEN a message is replayed, THE re-published message SHALL carry a new
   `eventId` (to avoid dedup collision), the original `correlationId`, and
   an additional header `dlq.replay.approval` containing the `ReplayApproval`
   reference.
5. THE replay action SHALL be idempotent: replaying the same
   `DeadLetteredMessage` twice (same `dlq.origin.offset`) SHALL result in
   exactly one re-published message, not two.
6. EVERY replay action SHALL be logged to the platform audit store with:
   the operator identity (synthetic), `ReplayApproval` reference,
   `dlq.origin.topic`, `dlq.origin.offset`, `tradeId`, and replay timestamp.
