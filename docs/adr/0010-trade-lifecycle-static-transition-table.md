# ADR-0010: Static Transition Table for Trade Lifecycle State Machine

## Status
Accepted

## Context
The `trade-lifecycle-service` manages trade state transitions through a defined set of states:
`INITIATED → VALIDATED → ENRICHED → MATCHED → SETTLED → COMPLETED` (with error branches to
`FAILED`, `SUSPENDED`, `CANCELLED`). The transition logic must be:

- Deterministic and auditable (regulators may ask "why did FX-000123 move to FAILED?")
- Fast (sub-millisecond per transition check)
- Visible in code review (no hidden rules buried in XML or DRL files)
- Testable with simple unit tests (given state + event → expected state)

The state space is bounded (~12 states, ~25 valid transitions) and changes infrequently (quarterly).

## Decision
Implement the state machine as a **static transition table** — an `EnumMap<State, Map<Event, Transition>>`
initialized at class-load time.

```java
private static final Map<TradeState, Map<LifecycleEvent, Transition>> TRANSITIONS = Map.ofEntries(
    entry(INITIATED, Map.of(VALIDATION_PASSED, to(VALIDATED), VALIDATION_FAILED, to(FAILED))),
    entry(VALIDATED, Map.of(ENRICHMENT_COMPLETE, to(ENRICHED), ENRICHMENT_TIMEOUT, to(SUSPENDED))),
    // ... all transitions declared here
);
```

A `Transition` record holds the target state, guard predicate (optional), and side-effect action.
Looking up a transition is O(1). If no entry exists for `(currentState, event)`, the transition is
illegal and the service emits a `TransitionDenied` domain event.

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| **Spring State Machine** | Heavy framework for 12 states; lifecycle tied to Spring context; hard to unit-test without Spring; adds significant dependency surface |
| **Drools for lifecycle rules** | Over-engineered for a bounded, stable state space; rule-file indirection harms readability; Drools reserved for complex risk calculations (ADR-0011) |
| **Database-driven transition config** | Adds runtime coupling to config tables; transitions rarely change; code review of a Map literal is simpler than reviewing DB migration scripts |

## Consequences

### Positive
- Entire state machine visible in one file (~60 lines) — easy to audit
- O(1) lookup, no framework overhead, no reflection
- Unit tests are trivial: `assertThat(machine.apply(INITIATED, VALIDATION_PASSED)).isEqualTo(VALIDATED)`
- No external dependencies beyond Java collections

### Negative
- Adding a new state requires a code change + redeployment (acceptable given quarterly cadence)
- No visual diagram auto-generated from code (mitigated by a PlantUML spec in docs)
- Complex guard predicates could grow; if they exceed 3 conditions, extract to a named predicate class

### Mitigations
- A PlantUML diagram in `docs/diagrams/` is regenerated from the transition map via a build plugin
- Guard predicates are tested independently and composed via `Predicate.and()`
