CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

CREATE TABLE books (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    isbn13 VARCHAR(13),
    isbn10 VARCHAR(10),
    title VARCHAR(500) NOT NULL,
    subtitle VARCHAR(500),
    author_names TEXT,
    category_names TEXT,
    description TEXT,
    language VARCHAR(16),
    publisher VARCHAR(300),
    published_year INTEGER,
    page_count INTEGER,
    cover_url TEXT,
    public_domain BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    search_document TSVECTOR GENERATED ALWAYS AS (
      to_tsvector('simple',
        coalesce(title, '') || ' ' ||
        coalesce(subtitle, '') || ' ' ||
        coalesce(author_names, '') || ' ' ||
        coalesce(category_names, '') || ' ' ||
        coalesce(description, '')
      )
    ) STORED
);
CREATE UNIQUE INDEX uq_books_isbn13 ON books(isbn13) WHERE isbn13 IS NOT NULL;
CREATE INDEX idx_books_isbn10 ON books(isbn10);
CREATE INDEX idx_books_search_document ON books USING GIN(search_document);

CREATE TABLE external_book_refs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    book_id UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    provider VARCHAR(64) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    source_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_external_book_provider_id UNIQUE(provider, external_id)
);
CREATE INDEX idx_external_book_refs_book ON external_book_refs(book_id);

CREATE TABLE user_library (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'WANT_TO_READ',
    favorite BOOLEAN NOT NULL DEFAULT false,
    rating SMALLINT,
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_library UNIQUE(user_id, book_id),
    CONSTRAINT ck_user_library_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5)
);
CREATE INDEX idx_user_library_user_status ON user_library(user_id, status);

CREATE TABLE reading_progress (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    current_page INTEGER NOT NULL DEFAULT 0,
    percentage NUMERIC(5,2) NOT NULL DEFAULT 0,
    started_at DATE,
    completed_at DATE,
    notes TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_reading_progress UNIQUE(user_id, book_id),
    CONSTRAINT ck_reading_page CHECK (current_page >= 0),
    CONSTRAINT ck_reading_percentage CHECK (percentage BETWEEN 0 AND 100)
);

CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id UUID REFERENCES books(id) ON DELETE SET NULL,
    original_name VARCHAR(500) NOT NULL,
    content_type VARCHAR(255),
    size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(700) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_documents_owner ON documents(owner_id, created_at DESC);

CREATE TABLE ai_generations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id UUID,
    prompt_type VARCHAR(64) NOT NULL,
    prompt_hash CHAR(64) NOT NULL UNIQUE,
    provider VARCHAR(80),
    model VARCHAR(120),
    result TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_generations_entity ON ai_generations(entity_type, entity_id, prompt_type);

CREATE TABLE ai_request_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    operation VARCHAR(80) NOT NULL,
    provider VARCHAR(80),
    model VARCHAR(120),
    input_tokens INTEGER,
    output_tokens INTEGER,
    latency_ms BIGINT NOT NULL,
    success BOOLEAN NOT NULL,
    error_type VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_request_logs_user_created ON ai_request_logs(user_id, created_at DESC);

CREATE TABLE vector_store (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    content TEXT,
    metadata JSON,
    embedding VECTOR(1536)
);
CREATE INDEX idx_vector_store_embedding_hnsw ON vector_store USING HNSW (embedding vector_cosine_ops);
CREATE INDEX idx_vector_store_document_owner ON vector_store ((metadata->>'documentId'), (metadata->>'ownerId'));
CREATE INDEX idx_vector_store_book_id ON vector_store ((metadata->>'bookId'));
