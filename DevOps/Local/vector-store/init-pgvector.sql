-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Similar incident embeddings (agent memory/recall)
CREATE TABLE IF NOT EXISTS incident_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trade_id VARCHAR(10),
    incident_type VARCHAR(50) NOT NULL,
    summary TEXT NOT NULL,
    embedding vector(1536) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Rule corpus embeddings (DRL rule search)
CREATE TABLE IF NOT EXISTS rule_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id VARCHAR(50) NOT NULL,
    rule_version VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    embedding vector(1536) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- HNSW index for fast similarity search
CREATE INDEX IF NOT EXISTS idx_incident_embedding ON incident_embeddings
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS idx_rule_embedding ON rule_embeddings
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- All identifiers use synthetic FX- prefix
COMMENT ON TABLE incident_embeddings IS 'Stores embeddings of past trade incidents for similar-failure retrieval. All trade_ids use FX- prefix (synthetic).';
COMMENT ON TABLE rule_embeddings IS 'Stores embeddings of Drools rule descriptions for rule corpus search. All rule_ids are fictional.';
