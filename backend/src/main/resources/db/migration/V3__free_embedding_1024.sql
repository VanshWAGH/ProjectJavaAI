-- Migration V3: Update vector store embedding column to 1024 dimensions
-- Enables 100% free OpenRouter embedding model: liquid/lfm-2.5-embedding-350m
ALTER TABLE vector_store ALTER COLUMN embedding TYPE VECTOR(1024);

DROP INDEX IF EXISTS vector_store_embedding_idx;

CREATE INDEX IF NOT EXISTS vector_store_embedding_idx 
    ON vector_store USING hnsw (embedding vector_cosine_ops);
