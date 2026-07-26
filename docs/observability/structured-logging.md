# Structured Logging Standard

This document defines the structured logging format for all services in the FX Trade Operations Intelligence platform.

## Format

Every service emits logs as **JSON structured entries** (not free text). This enables direct indexing and querying in Elasticsearch without additional parsing.

## Required Fields

Every log entry MUST contain the following fields:

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `timestamp` | ISO-8601 string | Time the log entry was generated | `2024-07-25T14:30:00.123Z` |
| `level` | keyword | Log severity level | `INFO`, `WARN`, `ERROR`, `DEBUG` |
| `service` | keyword | Service name (matches `spring.application.name`) | `trade-lifecycle-service` |
| `traceId` | keyword | OTel trace ID from active context | `4bf92f3577b34da6a3ce929d0e0e4736` |
| `spanId` | keyword | OTel span ID from active context | `00f067aa0ba902b7` |
| `correlationId` | keyword | Request correlation ID (from MDC) | `corr-abc-123` |
| `message` | text | Human-readable log message | `Trade state transition completed` |
| `logger` | keyword | Logger class name | `c.f.t.s.TradeLifecycleService` |

## Optional Fields (when applicable)

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `tradeId` | keyword | Trade identifier (FX- prefix) | `FX-000001` |
| `regionCode` | keyword | Region code | `APAC` |
| `exception` | text | Stack trace (separate from message) | `java.lang.IllegalStateException...` |

## Example Log Entries

### Standard INFO log

```json
{
  "timestamp": "2024-07-25T14:30:00.123Z",
  "level": "INFO",
  "service": "trade-lifecycle-service",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7",
  "correlationId": "corr-abc-123",
  "tradeId": "FX-000001",
  "regionCode": "APAC",
  "message": "Trade state transition completed: CAPTURED -> VALIDATED",
  "logger": "c.f.t.lifecycle.TradeLifecycleService"
}
```

### ERROR log with exception

```json
{
  "timestamp": "2024-07-25T14:30:01.456Z",
  "level": "ERROR",
  "service": "trade-lifecycle-service",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7",
  "correlationId": "corr-abc-123",
  "tradeId": "FX-000001",
  "regionCode": "APAC",
  "message": "Illegal state transition attempted",
  "logger": "c.f.t.lifecycle.TradeLifecycleService",
  "exception": "java.lang.IllegalStateException: Cannot transition from CLOSED to CAPTURED\n\tat com.fxtradeops..."
}
```

### Log without trace context (scheduled task)

```json
{
  "timestamp": "2024-07-25T14:30:00.000Z",
  "level": "INFO",
  "service": "eod-processing-service",
  "traceId": "",
  "spanId": "",
  "correlationId": "scheduled-eod-check",
  "message": "Scheduled EOD readiness check initiated",
  "logger": "c.f.t.eod.EodScheduler"
}
```

## Spring Boot Configuration

Structured JSON logging is configured via Logback in each service's `src/main/resources/logback-spring.xml`:

```xml
<configuration>
  <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <includeMdcKeyName>traceId</includeMdcKeyName>
      <includeMdcKeyName>spanId</includeMdcKeyName>
      <includeMdcKeyName>correlationId</includeMdcKeyName>
      <includeMdcKeyName>tradeId</includeMdcKeyName>
      <includeMdcKeyName>regionCode</includeMdcKeyName>
      <customFields>{"service":"${spring.application.name}"}</customFields>
    </encoder>
  </appender>

  <root level="INFO">
    <appender-ref ref="JSON" />
  </root>
</configuration>
```

The OTel Java agent automatically injects `traceId` and `spanId` into the MDC (Mapped Diagnostic Context), making them available to the Logback encoder without additional code.

## Log Pipeline

Logs flow through this pipeline:

```
Service (JSON stdout) → Docker logging driver → Logstash (TCP/5000) → Elasticsearch
```

### Logstash Processing

Logstash is configured to:
1. Parse incoming JSON structured logs (no additional grok parsing needed)
2. Add an `environment` field (`local`) for multi-environment filtering
3. Route Kafka broker logs to `fxops-kafka-*` indices
4. Route service logs to `fxops-services-*` indices

## Trace-Log Correlation

### From trace to logs

Given a `traceId` from Jaeger, query Kibana:
```
traceId: "4bf92f3577b34da6a3ce929d0e0e4736"
```

### From logs to trace

Every log entry with a non-empty `traceId` can be linked to Jaeger:
```
http://localhost:16686/trace/{traceId}
```

### When no trace context exists

Background/scheduled tasks emit `traceId: ""` and `spanId: ""`. Query for untraced code paths:
```
traceId: ""
```

## Index Patterns

| Pattern | Content | Retention |
|---------|---------|-----------|
| `fxops-services-*` | All platform service logs | 30 days |
| `fxops-kafka-*` | Kafka broker logs | 14 days |

## Saved Queries in Kibana

Pre-provisioned saved queries for common investigations:

1. **Trade Timeline by Trade ID** — All logs for a `tradeId` across services, chronological order
2. **Errors by Service** — `level=ERROR` grouped by service
3. **DLQ Events** — Logs related to dead-lettered messages
4. **Correlation ID Trace** — All logs for a `correlationId` across services

## Rules

- Log entries SHALL NOT contain real credentials, secrets, or PII
- Stack traces go in the `exception` field, NOT embedded in `message`
- All identifiers use the synthetic `FX-` prefix
- `tradeId` and `regionCode` MUST be top-level fields (not embedded in message text)
- When no active trace context exists, `traceId` and `spanId` are empty strings (not omitted)
