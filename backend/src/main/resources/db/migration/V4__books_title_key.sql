-- SPEC-04 §12.9a: dedup compares a normalized title (accents, case and punctuation removed) instead of lower(title).
-- Filled by the application on insert; existing rows are backfilled at startup (BookTitleKeyBackfill).
ALTER TABLE books ADD COLUMN title_key VARCHAR(500);
CREATE INDEX idx_books_title_key ON books(title_key);
DROP INDEX IF EXISTS idx_books_title_lower;
