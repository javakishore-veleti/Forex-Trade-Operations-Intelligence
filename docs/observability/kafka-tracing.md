# Kafka Trace Propagation

This document describes how distributed traces propagate through Apache Kafka (the Event Stream) in the FX Trade Operations Intelligence platform.

## Overview

The platform uses the **OpenTelemetry Java Agent** (auto-instrumentation) to automatically propagate W3C TraceContext through Kafka message headers. No manual code is required in service logic — the agent instruments Spring Kafka producers and consumers transparently.

## How It Works

### Producer Side (Automatic)

When a service publishes a message to Kafka via Spring Kafka's `KafkaTemplate`, the OTel Java agent automatically:

1. Creates a **ProducerSpan** representing the send operation
2. Injects the active `traceparent` header (and `tracestate` if present) into the Kafka record headers
3. Records the topic name as `messaging.destination.name`
4. Records the message key as `messaging.kafka.message.key`
5. Sets the span name to `{service-name} send {topic-name}`

The injected `traceparent` carries the `traceId` of the current unit of work (e.g., the HTTP request that triggered the publish), NOT a freshly generated `traceId`.

### Consumer Side (Automatic)

When a service consumes a message from Kafka via Spring Kafka's `@KafkaListener` or `MessageListener`, the OTel Java agent automatically:

1. Extracts the `traceparent` header from the incoming Kafka record
2. Creates a **ConsumerSpan** with the extracted trace context as the parent
3. Records topic name, partition, and offset as span attributes
4. Sets the span name to `{service-name} receive {topic-name}`

If no `traceparent` header is present (e.g., legacy messages), the agent starts a new root span.

### Batch Consumer Pattern

For batch consumers processing multiple records in a single poll:

- A batch processing span is created
- Individual `ProducerSpan`s are **linked** (not parented) to avoid a misleadingly wide trace tree
- Links preserve the causal relationship while keeping the trace structure navigable

## W3C TraceContext Headers

The following headers are injected/extracted on Kafka records:

| Header | Format | Example |
|--------|--------|---------|
| `traceparent` | `{version}-{trace-id}-{parent-id}-{trace-flags}` | `00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01` |
| `tracestate` | Vendor-specific key-value pairs | `fxops=correlation_id:corr-abc-123` |

## End-to-End Trace Flow Example

```
[trade-ingest-service]
  HTTP POST /api/v1/trades (root span)
    └─ send fxops.trade.captured (ProducerSpan)
         │ traceparent injected into Kafka headers
         ▼
[trade-lifecycle-service]
  receive fxops.trade.captured (ConsumerSpan, parent = ProducerSpan)
    ├─ process-lifecycle-transition (custom span)
    └─ send fxops.trade.events (ProducerSpan)
         │ traceparent injected into Kafka headers
         ▼
[risk-calculation-service]
  receive fxops.trade.events (ConsumerSpan, parent = ProducerSpan)
    ├─ evaluate-risk-rules (custom span)
    └─ send fxops.risk.completed (ProducerSpan)
         │
         ▼
[eod-processing-service]
  receive fxops.risk.completed (ConsumerSpan, parent = ProducerSpan)
    └─ check-region-readiness (custom span)
```

All spans in this flow share the same `traceId`, making end-to-end investigation possible.

## DLQ Trace Propagation

When a message is dead-lettered:

1. The **ConsumerSpan** is set to status `ERROR` with `error.type = dead_lettered`
2. A child span `{service-name} dead-letter {origin-topic}` is created
3. The original `traceparent` from the consumed message is propagated to the DLQ message headers
4. This ensures the DLQ consumer can continue the same trace when processing the dead-lettered message

### DLQ Span Attributes

| Attribute | Description |
|-----------|-------------|
| `dlq.origin.topic` | Topic from which the message was consumed |
| `dlq.origin.partition` | Partition of the original message |
| `dlq.origin.offset` | Offset of the original message |
| `dlq.failure.reason` | Reason for dead-lettering (max 200 chars) |
| `dlq.poison.flag` | Whether the message is classified as poison |

## Event Sequence Processor

The `event-sequence-processor` (Kafka Streams application) has special tracing behavior:

1. Creates a **StreamProcessingSpan** for each record consumed from `fxops.trade.events`
2. The span is **linked** to the source record's `ProducerSpan` via the extracted `traceparent`
3. When an anomaly is detected and published to `fxops.sequence.anomalies`, the publish span is a child of the `StreamProcessingSpan`
4. Span attributes include `trade.id` and `violationType` (if any)

## Configuration

Auto-instrumentation is configured entirely through environment variables. No code changes are needed.

### Required Environment Variables (per service)

```yaml
OTEL_SERVICE_NAME: ${spring.application.name}
OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4317
OTEL_PROPAGATORS: tracecontext,baggage
OTEL_INSTRUMENTATION_KAFKA_EXPERIMENTAL_SPAN_ATTRIBUTES: true
OTEL_TRACES_EXPORTER: otlp
OTEL_METRICS_EXPORTER: otlp
OTEL_LOGS_EXPORTER: none
```

### JVM Agent Attachment

The OTel Java agent is attached at JVM startup via:

```
-javaagent:/opt/opentelemetry-javaagent.jar
```

This is configured in each service's Docker Compose definition via the `JAVA_TOOL_OPTIONS` environment variable.

## What Auto-Instrumentation Covers

The OTel Java agent automatically instruments (no code required):

- Spring Kafka `KafkaTemplate` (producer)
- Spring Kafka `@KafkaListener` (consumer)
- Kafka Streams `KafkaStreams` (stream processing)
- Spring Web MVC (HTTP endpoints)
- Spring WebClient / RestTemplate (HTTP clients)
- JDBC (PostgreSQL queries)
- MongoDB driver
- Redis (Lettuce/Jedis)

## Baggage Propagation

Business context is propagated alongside trace context:

| Baggage Key | Source | Purpose |
|-------------|--------|---------|
| `tradeId` | Event envelope `tradeId` field | Filter all spans for a trade |
| `regionCode` | Event envelope `regionCode` field | Filter spans by region |

Baggage entries appear as span attributes (`trade.id`, `region.code`) on every span in the trace.

## Notes

- All example identifiers use the synthetic `FX-` prefix
- No sensitive data (PII, credentials, monetary values) is propagated in baggage
- The auto-instrumentation agent handles all injection/extraction — manual header manipulation in service code is NOT required and should NOT be implemented
