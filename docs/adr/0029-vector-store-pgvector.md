# ADR-0029: pgvector for Vector Store

## Status
Accepted

## Context
The agent layer requires vector similarity search for two use cases:
1. **Similar-incident retrieval** — when investigating a trade issue, find past incidents with similar characteristics to inform the agent's reasoning
2. **Rule corpus search** — when explaining risk rules, search rule descriptions semantically

A dedicated vector database (Qdrant, Weaviate, Pinecone, Chroma) or a vector extension on an existing database were the options.

## Decision
Use **pgvector** (PostgreSQL extension) as the `VECTOR_STORE`:
- Locally: a separate PostgreSQL 16 container with pgvector enabled (port 5433)
- Cloud/AWS: RDS PostgreSQL with pgvector extension (same instance class as main DB, or Amazon OpenSearch kNN)
- Cloud/Azure: Azure Database for PostgreSQL Flexible Server with pgvector (or Azure AI Search)

Embedding dimension: 1536 (compatible with OpenAI text-embedding-3-small and many open models).
Index type: HNSW (m=16, ef_construction=64) with cosine distance.

## Alternatives Considered
- **Qdrant** — excellent vector performance, but adds a new infrastructure component with its own operational burden; at our scale (<100K vectors) pgvector is sufficient
- **Weaviate** — feature-rich but heavyweight; schema management adds complexity
- **Pinecone** — managed but cloud-only; no local dev story; vendor lock-in
- **ChromaDB** — good for prototyping but not production-grade at scale
- **Same PostgreSQL instance** — simpler but risks mixing OLTP trade state with vector workloads; separate instance keeps concerns isolated

## Consequences
- No new infrastructure type to learn/operate — it's PostgreSQL with an extension
- HNSW indexes provide sub-millisecond search at <100K vectors
- Cloud deployment maps naturally to existing PostgreSQL managed services
- If scale exceeds pgvector's limits (>10M vectors), migrate to a dedicated vector DB without changing the embedding pipeline (just swap the connection)
- Sidecars generate embeddings and write to this store; agents query it via MCP tools
