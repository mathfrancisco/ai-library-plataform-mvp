-- JSON metadata GIN index for vector metadata filters (docs/03-domain-model.md).
-- Spring AI's pgvector filter converter queries metadata::jsonb with jsonpath (@@),
-- which jsonb_path_ops GIN indexes support.
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_gin
    ON vector_store USING GIN ((metadata::jsonb) jsonb_path_ops);

-- Keep updated_at honest regardless of which code path writes the row.
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_books_updated_at BEFORE UPDATE ON books FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_user_library_updated_at BEFORE UPDATE ON user_library FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_reading_progress_updated_at BEFORE UPDATE ON reading_progress FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_documents_updated_at BEFORE UPDATE ON documents FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX IF NOT EXISTS idx_books_created_at ON books(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_books_title_lower ON books(lower(title));

-- Align hash columns with the documented ERD (varchar) and the JPA mappings.
ALTER TABLE ai_generations ALTER COLUMN prompt_hash TYPE VARCHAR(64);
ALTER TABLE refresh_tokens ALTER COLUMN token_hash TYPE VARCHAR(64);
