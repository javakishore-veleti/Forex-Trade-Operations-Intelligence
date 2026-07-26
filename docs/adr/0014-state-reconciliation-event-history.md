# ADR-0014: Canonical State from Event History for State Reconciliation

## Status
Accepted

## Context
The `state-reconciliation-service` detects discrepancies between what a trade's state *should be*
(derived from its event history) and what it *currently is* in the operational database. For example,
trade `FX-000789` may show `ENRICHED` in the DB but its event stream shows `MATCHED` was emitted —
indicating a missed state update.

The reconciliation must:
- Determine the "correct" state authoritatively
- Operate without manual intervention for 95%+ of discrepancies
- Produce an auditable explanation of how the correct state was derived
- Handle out-of-order events (Kafka at-least-once delivery)

## Decision
Derive **canonical state by replaying the complete event history** for a trade through the same
transition table used by `trade-lifecycle-service` (ADR-0010).

```java
public TradeState deriveCanonicalState(TradeId tradeId) {
    List<DomainEvent> events = eventStore.getEventsFor(tradeId); // ordered by sequence number
    TradeState state = TradeState.INITIATED;
    for (DomainEvent event : events) {
        state = transitionTable.apply(state, event.type())
            .orElseThrow(() -> new InvalidEventSequence(tradeId, state, event));
    }
    return state;
}
```

The derived state is the **single source of truth**. If it differs from the DB, the reconciliation
service emits a `StateCorrection` event and updates the operational store.

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| **Majority vote (quorum across replicas)** | Assumes multiple independent state stores exist; we have one primary DB + event log — not a distributed consensus problem |
| **Timestamp-based (latest-write-wins)** | Clock skew between services can produce incorrect ordering; events have logical sequence numbers which are more reliable |
| **Version-vector reconciliation** | Appropriate for multi-master writes; our architecture has single-writer per aggregate — unnecessary complexity |
| **Manual triage for all discrepancies** | Does not scale; operators would face hundreds of discrepancies during incident recovery |

## Consequences

### Positive
- Deterministic: same event sequence always produces same canonical state
- Auditable: the full event chain is the explanation ("state is MATCHED because events 1–7 applied cleanly")
- Reuses the lifecycle transition table — no duplicate state logic
- Automated correction for 95%+ cases; only truly invalid sequences escalate to operators

### Negative
- Requires access to the full event history (Kafka retention must exceed reconciliation window)
- Replay cost scales with event count per trade (mitigated: average trade has 8–12 events)
- If the transition table itself has a bug, reconciliation propagates the same bug

### Mitigations
- Kafka retention set to 30 days; reconciliation window is 7 days (comfortable margin)
- Event replay is idempotent and cacheable — results stored per reconciliation run
- Transition table shared as a library from `trade-lifecycle-service` — single source, tested with 80+ cases
- Irreconcilable sequences (invalid transitions) emit `ReconciliationEscalation` for human review
