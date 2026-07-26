# ADR-0035: Agent Memory Architecture — Session, Episodic, and Semantic

## Status
Accepted

## Context
Agents need memory at three timescales: within a conversation turn (working memory), across a multi-turn session (session memory), and across all time for learning from past incidents (long-term memory). Each has different performance, persistence, and privacy requirements.

## Decision
Implement a **three-tier memory architecture**:

### Tier 1: Working Memory (per-turn, ephemeral)
- **Storage**: n8n workflow variables (in-node data)
- **Lifetime**: single workflow execution
- **Content**: current tool results, intermediate reasoning, the active trade context
- **No persistence** — lost when the execution completes

### Tier 2: Session Memory (multi-turn, short-lived)
- **Storage**: Redis (CACHE role) with key `session:{sessionId}` and 24h TTL
- **Lifetime**: a conversation session (supervisor agent multi-turn dialogue)
- **Content**: conversation history, user preferences, referenced trade IDs, accumulated context
- **Eviction**: TTL expiry; explicitly cleared on session end
- **Used by**: Supervisor agent (multi-turn), any agent called in follow-up mode

### Tier 3: Episodic/Semantic Memory (long-term, persistent)
- **Storage**: PostgreSQL (RELATIONAL_STORE) for structured episodic records + pgvector (VECTOR_STORE) for semantic similarity search
- **Lifetime**: indefinite (with 90-day active search window)
- **Content**:
  - Episodic: past investigation outcomes, resolution actions taken, HITL decisions (structured records)
  - Semantic: embedded summaries of past incidents for similarity retrieval
- **Used by**: Trade Lifecycle agent (similar past failures), DLQ Triage (known signatures), all agents via supervisor recall

### Memory access via MCP tools:
- `getSessionMemory(sessionId)` — reads Redis session context
- `saveSessionMemory(sessionId, context)` — writes/extends session context
- `findSimilarIncidents(embedding, topK)` — cosine search on pgvector
- `getEpisodicMemory(tradeId)` — structured past outcomes from PostgreSQL

### Privacy boundary:
- No PII in any memory tier
- Only synthetic `FX-` identifiers stored
- Session memory cleared on TTL (no indefinite conversation storage)
- Episodic memory contains outcomes, not raw conversation text

## Alternatives Considered
- **Single memory store for all tiers** (e.g. Redis for everything) — rejected; Redis is wrong for long-term persistent memory and similarity search
- **Vector DB only** (embed everything, search everything semantically) — rejected; structured episodic lookups (by tradeId, by date) need relational queries, not vector search
- **LLM context window as memory** (stuff history into prompt) — rejected; doesn't scale past ~10 turns; expensive; loses precision for multi-session recall
- **External memory service** (Mem0, MemGPT) — rejected; adds external dependency; our three-tier approach uses infrastructure we already have (Redis, PostgreSQL, pgvector)

## Consequences
- Each tier uses infrastructure already in the stack (no new systems)
- Session memory is fast (Redis) and ephemeral (24h TTL)
- Episodic memory is durable and queryable (PostgreSQL)
- Semantic recall is powered by the same pgvector used for other embeddings
- Agents access memory through MCP tools, not direct DB connections
- Memory is auditable (episodic records in PostgreSQL, not opaque vector-only)
