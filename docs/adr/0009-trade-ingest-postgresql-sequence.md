# ADR-0009: PostgreSQL Sequence for Trade ID Generation

## Status
Accepted

## Context
Every trade entering the platform receives a unique, human-readable identifier (e.g., `FX-000001`).
The `trade-ingest-service` is the single entry-point for new trades and must generate IDs that are:

- Globally unique across all service instances
- Monotonically increasing (operations teams sort/search by ID range)
- Human-readable and short (fit in UI columns, log grep, voice communication)
- Generated at high throughput (target: 5,000 trades/sec burst)

The ID format is `FX-{zero-padded sequence}` — a business-meaningful prefix plus a numeric sequence.

## Decision
Use a **PostgreSQL `SEQUENCE`** (`trade_id_seq`) with an allocation batch size of 50.

```sql
CREATE SEQUENCE trade_id_seq START 1 INCREMENT 1 CACHE 50;
```

The service fetches a batch of 50 values via `SELECT nextval('trade_id_seq') FROM generate_series(1,50)`
on startup and refills when the local pool drops below 10. The application formats the raw long as
`String.format("FX-%06d", seq)`.

Key properties:
- PostgreSQL guarantees uniqueness even under concurrent connections
- `CACHE 50` reduces round-trips; gaps on crash are acceptable (not every number need be used)
- Single-writer service design — only `trade-ingest-service` calls this sequence

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| **UUID v4** | 36-character string is unwieldy for human communication; not sortable by time; wastes index space |
| **UUID v7 (time-ordered)** | Still 36 characters; not human-readable; overkill for a single-writer service |
| **Distributed ID (Snowflake/TSID)** | Adds machine-ID coordination complexity; justified only when multiple writers exist |
| **Application-level AtomicLong** | Loses state on restart; requires persistence anyway; re-invents what the DB already provides |

## Consequences

### Positive
- Simple, battle-tested mechanism — no external coordination library
- IDs are short, sortable, and human-friendly (`FX-000042` reads well on a phone call)
- Batch fetching amortizes DB latency to ~1 call per 50 trades
- No single-point-of-failure beyond the DB, which is already required for trade persistence

### Negative
- Tight coupling to PostgreSQL — cannot trivially swap to a non-relational store
- Gaps in sequence on service crash (acceptable per business requirements)
- Sequence is single-region; multi-region would require range partitioning

### Mitigations
- The sequence lives in the same DB as the trade table — no additional infrastructure
- If multi-region is required, the sequence can be partitioned by prefix (`FX-1xxxxx`, `FX-2xxxxx`)
