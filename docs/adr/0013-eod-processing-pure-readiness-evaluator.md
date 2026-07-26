# ADR-0013: Pure Function ReadinessEvaluator for EOD Processing

## Status
Accepted

## Context
The `eod-processing-service` must determine whether end-of-day batch processing can proceed.
Readiness depends on multiple preconditions:

- All expected trade feeds received (e.g., 12/12 counterparty files for 2025-03-14)
- No trades in transient states (`INITIATED`, `SUSPENDED`) beyond threshold
- Risk calculations complete for all enriched trades
- Business calendar confirms today is a valid settlement date

The evaluator runs every 5 minutes from T+16:00 until readiness is achieved or a timeout forces
manual intervention. The logic must be deterministic, testable without infrastructure, and
produce an explainable result (operators need to know *why* EOD is not ready).

## Decision
Implement readiness evaluation as a **pure function** — `ReadinessEvaluator.evaluate(ReadinessInput) → ReadinessResult`.

```java
public class ReadinessEvaluator {
    public ReadinessResult evaluate(ReadinessInput input) {
        List<ReadinessViolation> violations = new ArrayList<>();
        if (input.feedsReceived() < input.feedsExpected())
            violations.add(new ReadinessViolation("FEEDS_INCOMPLETE", ...));
        if (input.transientTradeCount() > input.transientThreshold())
            violations.add(new ReadinessViolation("TRANSIENT_TRADES", ...));
        // ...
        return new ReadinessResult(violations.isEmpty(), violations, Instant.now());
    }
}
```

Key properties:
- No injected dependencies — input fully describes the world state
- Output enumerates all violations (not just first failure)
- Same input always produces same output — trivial to property-test
- Scheduling, data fetching, and notification are separate concerns in the orchestration layer

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| **Stateful evaluator (accumulates state across polls)** | Harder to reason about; partial state on restart requires recovery logic; violates idempotency |
| **Drools rules engine** | Overkill for 6 straightforward boolean conditions; Drools reserved for 200+ risk rules (ADR-0011) |
| **Event-sourced readiness (replay events to derive state)** | Adds event-store dependency; readiness is a point-in-time snapshot, not a history |
| **Stored procedure in PostgreSQL** | Couples logic to DB; untestable without DB; hard to version control |

## Consequences

### Positive
- Unit tests require zero mocking — construct input, assert output
- Operators see all blocking conditions at once (not sequential "fix one, discover next")
- Function can be called from multiple contexts (scheduled, manual trigger, health check)
- No hidden state — service restart has zero impact on correctness

### Negative
- Caller must assemble `ReadinessInput` from multiple sources (trade count, feed status, calendar)
- No incremental evaluation — re-evaluates all conditions every poll (acceptable at 5-min interval)
- Complex threshold tuning requires redeployment (externalized via Spring `@ConfigurationProperties`)

### Mitigations
- `ReadinessInputAssembler` service gathers data from repositories and constructs the input record
- Thresholds externalized to `application.yml` with environment-specific overrides
- `ReadinessResult` serialized to the EOD dashboard for operator visibility
