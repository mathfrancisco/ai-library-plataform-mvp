# Domain Model and Database

Generated from the Flyway migrations (`V1` core schema, `V2` triggers and hash column types, `V3` 384-dim embeddings
with jsonb metadata, `V4` normalized title key). Keep this file in sync when adding a migration.

## ERD

```mermaid
erDiagram
  USERS ||--o{ REFRESH_TOKENS : owns
  USERS ||--o{ USER_LIBRARY : has
  BOOKS ||--o{ USER_LIBRARY : appears_in
  USERS ||--o{ READING_PROGRESS : tracks
  BOOKS ||--o{ READING_PROGRESS : progress_for
  BOOKS ||--o{ EXTERNAL_BOOK_REFS : sourced_from
  USERS ||--o{ DOCUMENTS : uploads
  BOOKS o|--o{ DOCUMENTS : can_attach
  USERS o|--o{ AI_GENERATIONS : requests
  USERS o|--o{ AI_REQUEST_LOGS : emits

  USERS {
    uuid id PK
    varchar email UK
    varchar password_hash
    varchar display_name
    varchar role "USER | ADMIN"
    timestamptz created_at
    timestamptz updated_at
  }

  REFRESH_TOKENS {
    uuid id PK
    uuid user_id FK
    varchar token_hash UK "SHA-256, never the raw token"
    timestamptz expires_at
    timestamptz revoked_at
    timestamptz created_at
  }

  BOOKS {
    uuid id PK
    varchar isbn13 UK "partial unique, not null"
    varchar isbn10
    varchar title
    varchar title_key "normalized title for dedup"
    varchar subtitle
    text author_names "pipe-separated"
    text category_names "pipe-separated"
    text description
    varchar language
    varchar publisher
    int published_year
    int page_count
    text cover_url
    boolean public_domain
    tsvector search_document "generated"
    timestamptz created_at
    timestamptz updated_at
  }

  EXTERNAL_BOOK_REFS {
    uuid id PK
    uuid book_id FK
    varchar provider
    varchar external_id
    text source_url
    timestamptz created_at
  }

  USER_LIBRARY {
    uuid id PK
    uuid user_id FK
    uuid book_id FK
    varchar status "WANT_TO_READ | READING | READ | DROPPED"
    boolean favorite
    smallint rating "1-5 or null"
    timestamptz added_at
    timestamptz updated_at
  }

  READING_PROGRESS {
    uuid id PK
    uuid user_id FK
    uuid book_id FK
    int current_page
    numeric percentage "0-100"
    date started_at
    date completed_at
    text notes
    timestamptz updated_at
  }

  DOCUMENTS {
    uuid id PK
    uuid owner_id FK
    uuid book_id FK "nullable"
    varchar original_name
    varchar content_type
    bigint size_bytes
    varchar storage_key UK
    varchar status "STORED | PROCESSING | READY | FAILED"
    text error_message "failure code, e.g. NO_TEXT"
    int chunk_count
    timestamptz created_at
    timestamptz updated_at
  }

  AI_GENERATIONS {
    uuid id PK
    uuid user_id FK
    varchar entity_type
    uuid entity_id
    varchar prompt_type
    varchar prompt_hash UK
    varchar provider
    varchar model
    text result
    timestamptz created_at
  }

  AI_REQUEST_LOGS {
    uuid id PK
    uuid user_id FK
    varchar operation
    varchar provider
    varchar model "model actually used"
    int input_tokens
    int output_tokens
    bigint latency_ms
    boolean success
    varchar error_type
    timestamptz created_at
  }
```

`updated_at` columns are maintained by a trigger (`set_updated_at`, V2) as well as by the application.
Deleting a user cascades to refresh tokens, shelf items, progress and document rows; the application also removes
uploaded files, vector rows and AI logs/generations (`AccountDeletionService`).

## Vector storage

MVP uses Spring AI's PostgreSQL vector store table rather than separate physical vector tables per feature.

```sql
vector_store (id uuid PK, content text, metadata jsonb, embedding vector(384))
```

Each document chunk row carries metadata such as:

```json
{
  "type": "document_chunk",
  "ownerId": "uuid",
  "documentId": "uuid",
  "bookId": "uuid-or-absent",
  "chunkIndex": 12,
  "sourceName": "clean-architecture.pdf"
}
```

Book metadata vectors use `type=book` and `bookId`. Vector ids are deterministic UUIDs (per book, per
document + chunk index), so re-indexing replaces rows instead of duplicating them.

### Why not a generic Embedding JPA entity?

The vector store already owns persistence/index semantics. Duplicating vectors into a JPA entity would create two sources of truth. Domain tables store business data; `vector_store` stores retrieval documents.

## Important indexes

- `users(email)` unique.
- `books(isbn13)` unique where not null; `books(isbn10)`; `books(title_key)`; `books(created_at desc)`.
- `books.search_document` GIN (full-text search).
- `external_book_refs(provider, external_id)` unique.
- `user_library(user_id, book_id)` unique; `user_library(user_id, status)`.
- `reading_progress(user_id, book_id)` unique.
- `documents(owner_id, created_at desc)`.
- `vector_store.embedding` HNSW cosine index.
- `vector_store.metadata` GIN `jsonb_path_ops` — matches the `metadata::jsonb @@ jsonpath` filters Spring AI emits.

### Filtered HNSW search

pgvector applies metadata filters after the HNSW scan. With `hnsw.iterative_scan = relaxed_order` (set by V3 for
the database and by the Hikari connection init SQL) the scan continues until enough rows pass the owner filter, so a
user with few chunks still gets results when other users have thousands (`FilteredRetrievalRecallIT`).
Requires pgvector ≥ 0.8 (the `pgvector/pgvector:pg17` image).
