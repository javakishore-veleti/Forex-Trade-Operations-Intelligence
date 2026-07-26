# ADR-0030: Vector DB Schema and Index Management Strategy

## Status
Accepted

## Context
The vector store holds embeddings for two distinct use cases (incident recall and rule search) that evolve independently. Schema changes (new embedding dimensions, new metadata fields, new tables) and index rebuilds (changing HNSW parameters) must be managed without data loss or agent downtime.

## Decision
Adopt a **migration-based schema management** strategy for the vector store, mirroring the Flyway approach used for the RELATIONAL_STORE:

1. **Schema migrations** live in `DevOps/Local/vector-store/migrations/` as numbered SQL files (V1, V2, ...), applied in order at container startup
2. **One table per embedding domain** — `incident_embeddings` and `rule_embeddings` are separate tables, not a single polymorphic table, so they can evolve (dimensions, metadata) independently
3. **Index strategy**: HNSW for <1M vectors (current); switch to IVFFlat if scale exceeds 1M (requires ADR update). Index parameters (m, ef_construction) are documented in the migration that creates them
4. **Dimension changes**: a new embedding model with different dimensions (e.g. 1536→768) requires a new table version (e.g. `incident_embeddings_v2`) and a backfill job — never in-place alteration of the vector column
5. **Metadata schema**: the `metadata JSONB` column is schema-on-read; metadata conventions are documented in `DevOps/Local/vector-store/METADATA-SCHEMA.md`
6. **Retention**: embeddings older than 90 days with no query hits are candidates for archival (not deletion) — configurable via environment variable

## Alternatives Considered
- **Single polymorphic table** (type column + shared embedding column) — rejected because domain evolution would require migrations affecting all embeddings simultaneously
- **ORM-managed schema** (JPA/Hibernate for vector tables) — rejected because pgvector types are not well-supported by JPA; raw SQL migrations are clearer
- **No versioned migrations** (manual ALTER statements) — rejected because it's not reproducible across environments
- **Mutable indexes** (ALTER INDEX for parameter tuning) — rejected because HNSW index changes require a full rebuild; better to drop+recreate in a new migration

## Consequences
- Schema changes are auditable in Git (numbered migration files)
- Each embedding domain evolves independently
- Dimension changes are safe (new table version, backfill, swap)
- Index rebuilds are explicit and planned (new migration, not runtime surprise)
- METADATA-SCHEMA.md serves as the convention doc for what goes in JSONB
