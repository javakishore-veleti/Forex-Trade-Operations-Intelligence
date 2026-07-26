# ADR-0021: OTel Auto-Instrumentation Agent vs Manual Instrumentation

## Status
Accepted

## Context
The platform requires distributed tracing across 7 microservices, Kafka consumers, and HTTP calls.
A single trade like `FX-000042` generates spans across `trade-ingest-service` → Kafka →
`trade-lifecycle-service` → `risk-calculation-service`. Operators need end-to-end trace visibility
to diagnose latency and failures.

Instrumentation must cover:
- Inbound/outbound HTTP (Spring WebMVC, WebClient)
- Kafka producer/consumer context propagation (W3C TraceContext headers)
- JDBC queries (with statement attribution)
- Drools rule execution timing
- Custom business spans (e.g., "enrichment-phase", "risk-evaluation")

## Decision
Use the **OpenTelemetry Java auto-instrumentation agent** (`-javaagent:opentelemetry-javaagent.jar`)
as the primary instrumentation mechanism, supplemented by manual spans for business-critical sections.

```bash
java -javaagent:/opt/otel/opentelemetry-javaagent.jar \
     -Dotel.service.name=trade-lifecycle-service \
     -Dotel.exporter.otlp.endpoint=http://otel-collector:4317 \
     -jar trade-lifecycle-service.jar
```

Manual spans added only for business-meaningful boundaries:
```java
@WithSpan("trade.enrichment")
public EnrichedTrade enrich(@SpanAttribute("tradeId") String tradeId, RawTrade trade) { ... }
```

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| **Manual instrumentation only (OTel SDK)** | Requires explicit span creation for every HTTP/Kafka/JDBC call; massive boilerplate; easy to miss coverage gaps; maintenance burden across 7 services |
| **Micrometer Tracing only (no OTel)** | Narrower ecosystem; limited Kafka tracing support; no auto-instrumentation for JDBC; vendor lock-in to Micrometer-compatible backends |
| **Jaeger client library (direct)** | Deprecated in favor of OTel; no auto-instrumentation; ties us to Jaeger backend specifically |
| **AWS X-Ray SDK** | Cloud-specific; less community instrumentation coverage; does not support local-first development model |

## Consequences

### Positive
- Zero-code instrumentation for HTTP, Kafka, JDBC, gRPC — spans appear automatically
- W3C TraceContext propagation across Kafka works out-of-the-box (producer injects, consumer extracts)
- Vendor-neutral: OTLP export works with Jaeger, Tempo, Datadog, or any OTLP-compatible backend
- New services get full tracing by adding the agent JVM flag — no code changes
- Custom `@WithSpan` annotations add business context where auto-spans are too generic

### Negative
- Agent adds ~100ms to startup time (class-file transformation)
- Agent version must be compatible with Spring Boot version (occasional conflicts after upgrades)
- Auto-generated span names may not match domain language (e.g., `HTTP GET /api/trades` vs `fetchTrade`)
- Agent file must be distributed to all deployment environments

### Mitigations
- Span naming conventions documented in `docs/observability/span-naming.md` — custom spans override generic names for key operations
- Agent version pinned in Docker images and tested in CI against current Spring Boot version
- Docker images include the agent JAR at a fixed path (`/opt/otel/`)
- `otel.instrumentation.[name].enabled=false` disables noisy instrumentations selectively
