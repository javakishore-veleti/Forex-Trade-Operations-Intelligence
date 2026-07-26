# ADR-0011: Agent Memory Strategy

**Status:** Accepted

**Date:** 2024-02-12

## Context

Agents need memory at multiple timescales: short-term (within a single investigation), medium-term (across a session), and long-term (learned patterns from past investigations). The memory system must support context windows, avoid hallucination from stale data, and remain auditable.

Four strategies were evaluated:

1. **Redis session store** — key-value TTL-based memory for active sessions.
2. **PostgreSQL episodic memory** — structured records of past investigations with full-text search.
3. **Vector DB only** — embeddings of all interactions for semantic retrieval.
4. **In-workflow variables** — n8n's built-in workflow static/instance data.

## Decision

We adopt a **tiered memory architecture**:

| Tier | Store | TTL | Use Case |
|------|-------|-----|----------|
| Working memory | n8n workflow variables | Execution lifetime | Current investigation context, intermediate results |
| Session memory | Redis (hash per session) | 4 hours | Multi-turn conversation state, user preferences |
| Episodic memory | PostgreSQL `agent_episodes` table | 90 days | Completed investigation summaries, resolution patterns |
| Semantic recall | Vector DB (pgvector extension) | Indefinite | Similar-case retrieval for RAG enrichment |

### Implementation

- Working memory: n8n's `$workflow.variables` and expression references between nodes.
- Session memory: Redis hash `agent:session:{sessionId}` with fields for conversation history, current agent, and accumulated context.
- Episodic memory: After each completed investigation, a summary record is written to `agent_episodes(id, agent_type, trigger, resolution, duration_ms, created_at)`.
- Semantic recall: Episode summaries are embedded and stored in pgvector for retrieval when a new investigation starts.

### Example

Trade Lifecycle Reconstruction Agent investigating FX-007832:
- Working memory: current trace spans, intermediate API responses
- Session memory: user asked 3 follow-up questions (conversation history)
- Episodic recall: "Similar case FX-005119 resolved by identifying clock skew in matching engine"

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|-----------------|
| Redis only (all tiers) | No durability for episodic memory; TTL eviction loses valuable patterns |
| PostgreSQL only | Too slow for session-level read/write frequency; not optimized for semantic search |
| Vector DB only | Lacks structured query capability; episode metadata queries need relational model |
| In-workflow only | No cross-execution persistence; loses all context between runs |

## Consequences

### Positive
- Each tier optimized for its access pattern and durability requirements
- Episodic memory enables agents to learn from past investigations
- Auditable — all tiers are queryable for compliance review
- pgvector avoids adding a separate vector DB dependency

### Negative
- Three storage systems to maintain (Redis, PostgreSQL, pgvector)
- Consistency across tiers is eventual, not transactional
- Episode embedding quality depends on summarization prompt

### Mitigations
- Redis and PostgreSQL already in the platform stack — no new infrastructure
- pgvector is a PostgreSQL extension — same operational model
- Episode summarization uses a deterministic template, not free-form LLM output
