# Requirements Document — Azure Event Hub (Event Stream on Azure)

> **Technology-agnostic spec.** References `EVENT_STREAM` Technology Role from
> `01-initial-setup/01-technology-stack`. Resolves via `CloudTargetBinding` → Azure Event Hub (Kafka-compatible).
> Inherits `architecture-golden-path/01-service-nfrs`.

## Introduction

This spec defines requirements for the managed event streaming platform backing all domain events
in the Forex Trade Operations Intelligence platform on Azure. Azure Event Hub provides a Kafka-compatible
endpoint, allowing `SERVICE_FRAMEWORK` services to connect using the same Kafka client libraries
used locally. It covers namespace topology, consumer groups, partitioning, Kafka protocol connection,
and security. All example identifiers use synthetic `FX-` prefixes.

---

## Glossary

- **EventStream**: The `CloudTargetBinding` for `EVENT_STREAM` on Azure → Azure Event Hub (Kafka-compatible).
- **Namespace**: An Event Hub Namespace — the management container for Event Hub instances (analogous to a Kafka cluster).
- **EventHub**: A single Event Hub instance within the namespace (analogous to a Kafka topic).
- **ConsumerGroup**: A named group of consumers sharing offset tracking for a specific Event Hub.
- **KafkaProtocol**: The Apache Kafka wire protocol supported by Event Hub on port 9093 (SASL_SSL).
- **ThroughputUnit**: Capacity unit controlling ingress/egress bandwidth (Standard tier) or Processing Unit (Premium tier).

---

## Requirements

### Requirement 1: Namespace Topology and Tier

**User Story:** As a platform engineer, I want the event streaming namespace deployed with
sufficient capacity and zone redundancy, so that event throughput meets trade-volume SLA.

#### Acceptance Criteria

1. THE `EventStream` SHALL be deployed as an Event Hub Namespace at Premium or Standard tier with Kafka protocol enabled.
2. THE namespace SHALL enable zone redundancy (availability zones) for HA.
3. THE namespace SHALL be provisioned with sufficient throughput units (or processing units for Premium) to handle peak trade volume (≥ 1,000 events/sec sustained).
4. THE namespace SHALL reside in the same region and VNet as the AKS cluster.
5. AUTO-INFLATE (auto-scale throughput units) SHALL be enabled with a defined cap for Standard tier, or auto-scale for Premium.

---

### Requirement 2: Event Hub (Topic) Configuration

**User Story:** As a service developer, I want each domain event type mapped to a dedicated
Event Hub with appropriate partition count, so that ordering and parallelism are balanced.

#### Acceptance Criteria

1. EACH Kafka topic defined in the platform's topic registry SHALL map to a separate Event Hub instance.
2. PARTITION counts SHALL match the local Kafka topic design (e.g., `trade.captured` = 12 partitions).
3. MESSAGE retention SHALL be at least 7 days (configurable per Event Hub).
4. THE platform SHALL support at least 25 Event Hub instances (matching the domain event catalogue).
5. PARTITION key strategy SHALL use `tradeId` for trade-scoped events to guarantee per-trade ordering.

---

### Requirement 3: Consumer Groups

**User Story:** As a service developer, I want dedicated consumer groups per consuming service,
so that each service tracks its own offsets independently.

#### Acceptance Criteria

1. EACH consuming service SHALL have a dedicated consumer group on every Event Hub it reads from.
2. CONSUMER groups SHALL follow the naming pattern `<service-name>-cg` (e.g., `trade-lifecycle-cg`).
3. THE `$Default` consumer group SHALL NOT be used by application services (reserved for monitoring).
4. CONSUMER group count SHALL not exceed the tier limit; Premium tier is preferred for > 20 consumer groups.

---

### Requirement 4: Kafka Protocol Connection from Spring Boot

**User Story:** As a service developer, I want to connect to Event Hub using the standard Kafka
client configuration, so that no application code changes are required vs. local Kafka.

#### Acceptance Criteria

1. SERVICES SHALL connect to Event Hub using `spring.kafka.bootstrap-servers` pointing to `<namespace>.servicebus.windows.net:9093`.
2. THE SASL mechanism SHALL be `PLAIN` with connection string or OAuth (Managed Identity) token as credentials.
3. SERVICES SHALL use the same `spring.kafka.producer.*` and `spring.kafka.consumer.*` properties as local, with only bootstrap servers and security properties overridden per environment.
4. MANAGED IDENTITY authentication (OAuth bearer token via `OAUTHBEARER` SASL mechanism) SHALL be preferred over connection strings in production.
5. THE Kafka Streams library (`STREAM_PROCESSING`) SHALL be validated for compatibility with Event Hub Kafka protocol.

---

### Requirement 5: Security

**User Story:** As a security engineer, I want event streaming secured with private networking
and identity-based access, so that no data transits the public internet.

#### Acceptance Criteria

1. THE namespace SHALL use Private Endpoint integration — no public network access.
2. ALL connections SHALL use TLS (port 9093, SASL_SSL); plaintext connections SHALL be rejected.
3. ACCESS SHALL be granted via Azure RBAC roles (`Azure Event Hubs Data Sender`, `Azure Event Hubs Data Receiver`) assigned to pod managed identities.
4. NAMESPACE-level shared access policies (connection strings) SHALL be restricted to deployment automation only; runtime access uses Managed Identity.
5. DIAGNOSTIC logs (runtime, operational) SHALL be enabled and sent to Azure Monitor.

---

### Requirement 6: Schema Compatibility

**User Story:** As an event schema owner, I want schema evolution governed to prevent breaking
changes, so that producers and consumers evolve independently.

#### Acceptance Criteria

1. THE platform SHALL use Azure Schema Registry (Event Hub namespace feature) or maintain schema contracts in code (`shared-domain-contracts`).
2. SCHEMA compatibility mode SHALL be BACKWARD by default — new schema can read old data.
3. PRODUCER services SHALL embed schema version in event headers.
4. CONSUMER services SHALL validate incoming events against the expected schema version.

---

### Requirement 7: Cost Considerations

**User Story:** As a FinOps stakeholder, I want event streaming costs controlled per environment.

#### Acceptance Criteria

1. PRODUCTION SHALL use Premium tier with auto-scale processing units for guaranteed capacity.
2. DEV/TEST environments SHALL use Standard tier with minimal throughput units (1-2 TU).
3. RETENTION period in dev SHALL be reduced to 1 day to minimize storage costs.
4. ALL Event Hub resources SHALL be tagged with `project:fxops`, `environment:<env>`.
