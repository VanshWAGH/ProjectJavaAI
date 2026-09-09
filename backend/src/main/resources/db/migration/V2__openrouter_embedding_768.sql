-- Migrate vector embeddings to 768 dimensions for OpenRouter embedding models
-- (e.g., baai/bge-base-en-v1.5, sentence-transformers/all-mpnet-base-v2).
-- We drop and recreate the embedding column with the 768 dimension.
-- Any previously indexed repositories will be re-indexed.

ALTER TABLE vector_store DROP COLUMN IF EXISTS embedding;
ALTER TABLE vector_store ADD COLUMN embedding VECTOR(768);

-- Recreate cosine similarity index for the 768-dimension vector
DROP INDEX IF EXISTS vector_store_embedding_idx;
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx ON vector_store USING hnsw (embedding vector_cosine_ops);
