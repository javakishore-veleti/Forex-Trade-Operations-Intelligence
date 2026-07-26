# ADR-0004: Determinism and LLM Boundary

## Status
Accepted

## Context
A financial platform requires reproducible outcomes for risk, state, and exposure calculations. LLMs are non-deterministic by nature. The platform also uses AI agents for investigation and coordination.

## Decision
Enforce a strict boundary:
- **Service layer (Java/Spring Boot)**: ALL official figures (risk amounts, state transitions, calendar logic, limit checks, reconciliation) are computed deterministically with exact arithmetic (BigDecimal, no floating point). Identical inputs always produce identical outputs.
- **Agent layer (n8n + LLM)**: Agents interpret, explain, correlate, and coordinate. They never compute official numbers. They propose actions but never execute without human approval for M/H-risk operations.
- **Sidecar layer (Python)**: Statistical detection only. Detects anomalies and emits compact envelopes. Never business logic.

## Consequences
- All risk/state is auditable and reproducible
- Agents add intelligence without compromising correctness
- LLM non-determinism is confined to interpretation, not computation
- Every M/H action goes through: propose → deterministic simulation → impact report → human approval → controlled execution
