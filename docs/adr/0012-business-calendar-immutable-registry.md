# ADR-0012: Immutable In-Memory CalendarRegistry at Startup

## Status
Accepted

## Context
The `business-calendar-service` provides settlement-date and holiday lookups for 40+ currency
calendars. Every trade enrichment, EOD check, and risk calculation queries this service to answer:
"Is 2025-03-14 a good business day for USD/JPY settlement?"

Calendar data changes infrequently (annual holiday schedules published months in advance). The
service receives ~50,000 queries/day during peak EOD processing. Latency must be sub-millisecond
to avoid becoming a bottleneck in the trade enrichment pipeline.

## Decision
Load all calendar data into an **immutable in-memory `CalendarRegistry`** at application startup.

```java
@Component
public class CalendarRegistry {
    private final Map<Currency, BusinessCalendar> calendars; // unmodifiable

    @PostConstruct
    void init() {
        this.calendars = Map.copyOf(calendarRepository.loadAll());
    }

    public boolean isBusinessDay(Currency ccy, LocalDate date) {
        return calendars.get(ccy).isBusinessDay(date);
    }
}
```

Key properties:
- `Map.copyOf()` produces a truly immutable map — no accidental mutation
- Startup fails fast if calendar data is missing (fail-safe behavior)
- Refresh triggered by admin endpoint (`POST /calendars/reload`) for mid-year amendments
- Each `BusinessCalendar` is a pre-computed `Set<LocalDate>` of holidays for the next 2 years

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| **Per-request DB query** | Adds 2–5ms latency per call; 50K calls/day = unnecessary DB load for static data |
| **Cache-aside (Redis/Caffeine)** | Adds cache-invalidation complexity for data that changes ~2x/year; over-engineering |
| **Distributed config (Consul/etcd)** | Operational overhead of a config cluster for 40 small holiday lists; network hop for every lookup |
| **Lazy-load on first access** | Cold-start latency spikes; harder to validate completeness at startup |

## Consequences

### Positive
- O(1) lookup, zero network hops, zero GC pressure (immutable, long-lived objects)
- Startup validation ensures all required calendars are present before accepting traffic
- Thread-safe by construction — no synchronization needed
- Trivial to unit-test (inject a test registry with synthetic holidays)

### Negative
- Memory footprint grows with calendar horizon (2 years × 40 currencies ≈ ~200KB — negligible)
- Updates require explicit reload endpoint call or service restart
- If DB is unavailable at startup, service refuses to start (by design — fail-fast)

### Mitigations
- Health check reports calendar data age; alerts if > 30 days since last reload
- Reload endpoint is idempotent and atomic (swap reference, never partial update)
- Kubernetes readiness probe gates traffic until registry initialization completes
