# Requirements Document — Kafka Topic Design

> **Technology-agnostic spec.** References **Technology Roles** from
> `01-initial-setup/01-technology-stack`. Contains no product names or versions.

## Introduction

This feature defines the **authoritative topic design** for the platform's
`EVENT_STREAM`. It covers topic naming conventions, partition strategy,
replication factor, retention policy, schema registry integration, and the
mapping of each domain event type to its topic. Every microservice that
produces or consumes events MUST follow these conventions. The topic design
is the contract between producers and consumers; changing it is a breaking
change that requires an Architecture Decision Record.

All topic names, partition keys, and schema subjects use the `fxops.` namespace
prefix and fictional identifiers only. No real topic names, schemas, or
credentials are defined here.

---

## Glossary

- **Topic**: A named, partitioned, ordered log on the `EVENT_STREAM`.
- **TopicName**: A dot-separated identifier following the pattern
  `fxops.{domain}.{entity}[.{qualifier}]` (e.g. `fxops.trade.events`).
- **Partition**: A unit of parallelism within a `Topic`; messages with the
  same partition key land on the same `Partition` and are ordered within it.
- **PartitionKey**: The field used to route a message to a `Partition`;
  always a business key (e.g. `tradeId`) so all events for one trade are
  ordered on one `Partition`.
- **ReplicationFactor**: The number of broker replicas that hold a copy of each
  `Partition`; must be ≥ 2 in any multi-broker environment.
- **RetentionPolicy**: The per-`Topic` rule governing how long messages are
  retained (by time or by size) before they are eligible for deletion.
- **SchemaRegistry**: The `EVENT_STREAM` schema registry that stores and
  validates Avro/JSON schemas for every event type; bound to the `EVENT_STREAM`
  role in the technology stack.
- **SchemaSubject**: The registry key under which a schema version is stored;
  follows the pattern `{TopicName}-value`.
- **CompatibilityMode**: The schema evolution rule enforced by the
  `SchemaRegistry` (BACKWARD, FORWARD, FULL, NONE).
- **DLQ**: Dead-Letter Queue — a `Topic` that receives messages that a consumer
  failed to process after exhausting its retry budget.
- **DLQTopicName**: Follows the pattern `fxops.dlq.{origin-topic-short-name}`.
- **InternalTopic**: A `Topic` used for `STREAM_PROCESSING` internal state
  (changelog, repartition); named with the `fxops.internal.` prefix.

---

## Requirements

### Requirement 1: Topic Naming Convention

**User Story:** As a developer producing or consuming events, I want a single,
enforceable naming convention for all topics, so that I can infer a topic's
domain, entity, and purpose from its name without consulting external
documentation.

#### Acceptance Criteria

1. EVERY `Topic` on the platform `EVENT_STREAM` SHALL follow the naming pattern
   `fxops.{domain}.{entity}[.{qualifier}]`, where `domain` and `entity` are
   lowercase dot-separated words and the optional `qualifier` further
   distinguishes variants (e.g. `fxops.trade.events`, `fxops.risk.results`,
   `fxops.eod.status`).
2. EVERY `DLQ` `Topic` SHALL follow the naming pattern
   `fxops.dlq.{origin-topic-short-name}` (e.g. `fxops.dlq.trade-events`).
3. EVERY `InternalTopic` used by `STREAM_PROCESSING` SHALL follow the naming
   pattern `fxops.internal.{processor-name}.{purpose}`.
4. NO `Topic` name SHALL contain uppercase letters, spaces, or underscores;
   words within a segment SHALL be separated by hyphens.
5. THE following topics SHALL be defined as the initial set:

   | TopicName | Domain events carried |
   |---|---|
   | `fxops.trade.events` | All trade lifecycle domain events (TradeCaptured → TradeSettled, TradeAmended, TradeCancelled) |
   | `fxops.risk.results` | RiskCalculationCompleted, RiskCalculationFailed |
   | `fxops.risk.requests` | RiskCalculationRequested |
   | `fxops.eod.status` | RegionalCloseStarted, RegionalCloseReady, RegionalCloseClosed, GlobalConsolidationCompleted |
   | `fxops.sequence.anomalies` | SequenceViolationDetected (emitted by the event-sequence-processor) |
   | `fxops.dlq.trade-events` | DLQ for `fxops.trade.events` |
   | `fxops.dlq.risk-requests` | DLQ for `fxops.risk.requests` |

---

### Requirement 2: Partition Strategy

**User Story:** As a platform engineer, I want a consistent partition strategy
across all topics so that related events are co-located for ordering, and
parallelism scales predictably with load.

#### Acceptance Criteria

1. THE `PartitionKey` for every event on `fxops.trade.events`,
   `fxops.risk.requests`, `fxops.risk.results`, and
   `fxops.sequence.anomalies` SHALL be `tradeId`, so that all events for a
   single trade are ordered on one `Partition`.
2. THE `PartitionKey` for every event on `fxops.eod.status` SHALL be
   `regionCode`, so that all EOD status events for a region are ordered on
   one `Partition`.
3. THE initial partition count for domain event topics SHALL be a minimum of
   **6 partitions** to allow consumer-group parallelism across the six
   microservices that consume events at steady state; the count SHALL be
   documented in the topic's schema registry metadata and SHALL NOT be reduced
   without an ADR.
4. `DLQ` topics SHALL carry the same `PartitionKey` as their origin topic.
5. `InternalTopic` partition counts are managed by `STREAM_PROCESSING` and
   SHALL follow the `STREAM_PROCESSING` co-partitioning requirement with their
   source topic.

---

### Requirement 3: Replication Factor

**User Story:** As a platform engineer, I want a defined replication factor for
every topic so that the platform tolerates broker failures without data loss.

#### Acceptance Criteria

1. EVERY domain event topic SHALL have a `ReplicationFactor` of at least **3**
   in any environment with 3 or more brokers.
2. IN a single-broker local development environment, the `ReplicationFactor`
   MAY be reduced to **1** only; any compose or environment configuration that
   sets replication factor < 3 SHALL include a comment marking it as
   local-only.
3. `DLQ` topics SHALL carry the same `ReplicationFactor` as their origin topic.
4. THE minimum in-sync replicas (`min.insync.replicas`) for every domain event
   topic in a multi-broker environment SHALL be set to `ReplicationFactor − 1`
   (e.g. 2 of 3), so that one broker can fail without rejecting producer writes.

---

### Requirement 4: Retention Policy

**User Story:** As an operator, I want a defined, per-topic retention policy so
that storage is bounded and audit/replay windows are predictable.

#### Acceptance Criteria

1. THE `fxops.trade.events` topic SHALL retain messages for a minimum of **30
   days** to support trade lifecycle replay, investigation, and reconciliation
   use cases.
2. THE `fxops.risk.results` and `fxops.risk.requests` topics SHALL retain
   messages for a minimum of **7 days** to support same-week risk reprocessing.
3. THE `fxops.eod.status` topic SHALL retain messages for a minimum of **90
   days** to support EOD audit queries.
4. THE `fxops.sequence.anomalies` topic SHALL retain messages for a minimum of
   **30 days** to support anomaly investigation.
5. `DLQ` topics SHALL retain messages for a minimum of **14 days** and SHALL
   NOT be subject to automatic compaction; messages must remain retrievable for
   triage within the retention window.
6. `InternalTopic`s SHALL use log-compaction retention, not time-based
   retention, because they represent state changelogs.

---

### Requirement 5: Schema Registry Integration

**User Story:** As a producer or consumer developer, I want all event schemas
registered and enforced by the `SchemaRegistry` so that schema evolution is
controlled and backward-incompatible changes are detected before they reach
production.

#### Acceptance Criteria

1. EVERY event type published to any domain topic SHALL have its schema
   registered in the `SchemaRegistry` under the `SchemaSubject`
   `{TopicName}-value` before the first producer is deployed.
2. THE default `CompatibilityMode` for all `SchemaSubject`s SHALL be
   **BACKWARD**, so that new schema versions can be read by consumers built
   against the previous version.
3. WHERE a breaking schema change is required, an ADR SHALL be created before
   the new schema version is registered, and the `CompatibilityMode` for that
   subject SHALL be explicitly widened to FULL or NONE only for the duration of
   the migration.
4. EVERY producer SHALL validate outbound event payloads against the registered
   schema before publishing; a payload that fails schema validation SHALL NOT be
   published to the topic and SHALL be logged with the `CorrelationId`.
5. THE `SchemaRegistry` SHALL be reachable as a readiness dependency for every
   producer service (per `architecture-golden-path/01-service-nfrs` GP-Rq-4);
   a producer service SHALL NOT be considered ready if the `SchemaRegistry` is
   unreachable.

---

### Requirement 6: Topic Configuration as Code

**User Story:** As a platform engineer, I want all topic definitions expressed as
code or configuration checked into the repository, so that topic creation is
reproducible, reviewable, and version-controlled.

#### Acceptance Criteria

1. EVERY topic defined in Requirement 1 SHALL have a corresponding declarative
   configuration entry (partition count, replication factor, retention policy,
   `PartitionKey` documentation) in a topic-registry file located under
   `DevOps/Local/EVENT_STREAM/topics/`.
2. THE topic-registry file SHALL be the single source of truth for topic
   configuration; no topic SHALL be created by ad-hoc CLI commands that are not
   reflected in the registry file.
3. THE `DevOps/Local/EVENT_STREAM/` compose setup SHALL include an
   initialization step that creates all topics from the registry file on first
   startup, so that a clean local environment is fully provisioned without
   manual steps.
4. WHEN a new topic is required by a feature spec, THE topic-registry file SHALL
   be updated in the same pull request as the feature that introduces it.
5. ALL topic names, retention values, and partition counts in the registry file
   SHALL use `Synthetic_Identifier`-safe values; no real topic names from any
   production system SHALL be used.
