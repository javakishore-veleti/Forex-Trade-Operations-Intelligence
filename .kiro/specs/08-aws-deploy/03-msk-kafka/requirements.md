# Requirements Document — MSK Kafka (Event Stream on AWS)

> **Technology-agnostic spec.** References `EVENT_STREAM` Technology Role from
> `01-initial-setup/01-technology-stack`. Resolves via `CloudTargetBinding` → AWS MSK.
> Inherits `architecture-golden-path/01-service-nfrs`.

## Introduction

This spec defines requirements for the managed event-streaming platform that carries all domain
events between `Middleware/` services. It covers cluster topology, topic provisioning automation,
schema governance, monitoring, security, and performance tuning. All example identifiers use
synthetic `FX-` prefixes.

---

## Glossary

- **EventStream**: The `CloudTargetBinding` for `EVENT_STREAM` on AWS → Amazon MSK.
- **Broker**: A single Kafka node in the MSK cluster.
- **TopicRegistry**: The declarative list of platform topics (from `03-events/01-kafka-topic-design`).
- **SchemaRegistry**: The service validating event schemas (Glue or self-managed).
- **MSKConnect**: Managed Kafka Connect for running connectors (monitoring, sink).
- **BrokerConfig**: The set of cluster-level Kafka broker parameters.

---

## Requirements

### Requirement 1: Cluster Topology

**User Story:** As a platform engineer, I want the event-stream cluster deployed with at least
3 brokers across multiple AZs, so that broker failure does not cause event loss or downtime.

#### Acceptance Criteria

1. THE `EventStream` cluster SHALL have a minimum of 3 brokers, one per availability zone.
2. THE cluster SHALL use a provisioned-throughput or adequately-sized instance type for the expected event volume (1000+ events/sec peak).
3. THE cluster SHALL run the Kafka version compatible with the `PinnedVersion` in the technology-stack registry (Kafka 3.x).
4. ALL brokers SHALL be deployed in private subnets with no public access.
5. THE cluster SHALL enable tiered storage for cost-efficient long-term retention.

---

### Requirement 2: Topic Provisioning Automation

**User Story:** As a developer, I want topics created automatically from the topic-registry
definition, so that the AWS environment matches local topic topology without manual steps.

#### Acceptance Criteria

1. TOPIC creation SHALL be driven by the same `topic-registry.yml` used locally (single source of truth from `03-events/01-kafka-topic-design`).
2. THE provisioning process SHALL create all domain topics and DLQ topics with correct partitions, replication factor, and retention per the registry.
3. TOPIC creation SHALL be idempotent — re-running SHALL NOT fail or alter existing topics that already match.
4. TOPIC configuration changes (retention, partitions) SHALL be applied via the same automation.

---

### Requirement 3: Security

**User Story:** As a security engineer, I want all event-stream traffic encrypted and access
controlled via IAM, so that no unauthorized service can produce or consume events.

#### Acceptance Criteria

1. ALL client-broker communication SHALL use TLS (in-transit encryption); plaintext listeners SHALL be disabled.
2. INTER-BROKER communication SHALL use TLS encryption.
3. CLIENT authentication SHALL use IAM-based access control (no SASL/PLAIN username/password).
4. AUTHORIZATION SHALL enforce per-topic ACLs: each service can only produce/consume its declared topics.
5. THE cluster SHALL be accessible only from within the VPC (private endpoints).

---

### Requirement 4: Schema Registry

**User Story:** As a platform engineer, I want schemas validated and versioned, so that
producers cannot publish incompatible event structures.

#### Acceptance Criteria

1. A schema registry SHALL be deployed alongside the `EventStream` cluster (AWS Glue Schema Registry or self-managed).
2. SCHEMAS SHALL enforce BACKWARD compatibility by default (matching the local `03-events/01-kafka-topic-design` spec).
3. EVERY domain topic SHALL have a registered value schema.
4. PRODUCERS SHALL validate messages against the schema before publishing (producer-side validation).

---

### Requirement 5: Monitoring and Alerting

**User Story:** As an operator, I want MSK metrics and consumer lag visible in the observability
stack, so that broker health and consumer performance are monitored.

#### Acceptance Criteria

1. BROKER metrics (CPU, disk, network, under-replicated partitions) SHALL be exported to the observability stack.
2. CONSUMER lag per consumer group SHALL be monitored with alerting thresholds.
3. THE cluster SHALL emit JMX/CloudWatch metrics for partition count, message rate, and bytes in/out.
4. ALERTS SHALL fire for: under-replicated partitions > 0, consumer lag > 10000 messages, disk > 80%.

---

### Requirement 6: Backup and Retention

**User Story:** As a compliance officer, I want event retention and replay capabilities, so that
events can be reprocessed for recovery or audit.

#### Acceptance Criteria

1. DOMAIN topics SHALL retain events per the retention policy in the topic-registry (7–90 days by topic).
2. DLQ topics SHALL retain events for at least 14 days.
3. THE platform SHALL support consumer offset reset for replay scenarios.
4. TIERED storage SHALL be used for retention beyond 7 days to minimize hot-storage costs.

---

### Requirement 7: Cost Considerations

**User Story:** As a FinOps stakeholder, I want the cluster sized for actual load with cost controls.

#### Acceptance Criteria

1. DEV environments SHALL use the smallest broker type with 2 brokers (relaxed HA).
2. PRODUCTION SHALL use provisioned throughput sized for peak load + 30% headroom.
3. TIERED storage SHALL reduce storage costs for long-retention topics.
4. CLUSTER scaling (broker count, storage) SHALL be documented but not auto-scaled without approval.
