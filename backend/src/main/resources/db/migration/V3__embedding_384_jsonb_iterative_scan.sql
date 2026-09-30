-- SPEC-02 + SPEC-04 §12.1–12.2.
-- Embeddings move to local all-MiniLM-L6-v2 (384 dims). Existing vectors were produced by another
-- model and cannot be compared with new ones, so the table is emptied: books are re-indexed on the
-- next write or via POST /api/admin/books/reindex, documents via POST /api/documents/{id}/reingest.

DROP INDEX IF EXISTS idx_vector_store_embedding_hnsw;
DROP INDEX IF EXISTS idx_vector_store_document_owner;
DROP INDEX IF EXISTS idx_vector_store_book_id;
DROP INDEX IF EXISTS idx_vector_store_metadata_gin;

TRUNCATE vector_store;

ALTER TABLE vector_store ALTER COLUMN embedding TYPE vector(384);

-- Spring AI filters query metadata::jsonb with jsonpath (@@); a jsonb column plus a jsonb_path_ops GIN
-- index lets those filters use the index.
ALTER TABLE vector_store ALTER COLUMN metadata TYPE jsonb USING metadata::jsonb;
CREATE INDEX idx_vector_store_metadata_gin ON vector_store USING GIN (metadata jsonb_path_ops);

CREATE INDEX idx_vector_store_embedding_hnsw ON vector_store USING HNSW (embedding vector_cosine_ops);

-- pgvector filters after the HNSW scan (ef_search candidates, default 40). With many chunks from other
-- tenants a filtered query can come back empty. Iterative scans (pgvector >= 0.8) keep scanning until
-- enough rows pass the filter.
DO $$
BEGIN
    IF (SELECT string_to_array(extversion, '.')::int[] >= ARRAY[0, 8]
        FROM pg_extension WHERE extname = 'vector') THEN
        EXECUTE format('ALTER DATABASE %I SET hnsw.iterative_scan = %L', current_database(), 'relaxed_order');
    ELSE
        RAISE WARNING 'pgvector < 0.8: hnsw.iterative_scan unavailable; tenant-filtered search may miss rows';
    END IF;
END
$$;
