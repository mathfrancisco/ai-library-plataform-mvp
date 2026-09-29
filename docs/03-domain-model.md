# Domain Model and Database

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
  USERS ||--o{ AI_GENERATIONS : requests
  USERS ||--o{ AI_REQUEST_LOGS : emits

  USERS {
    uuid id PK
    varchar email UK
    varchar password_hash
    varchar display_name
    varchar role
    timestamptz created_at
  }

  BOOKS {
    uuid id PK
    varchar isbn13 UK
    varchar isbn10
    varchar title
    varchar subtitle
    text author_names
    text category_names
    text description
    varchar language
    varchar publisher
    int published_year
    int page_count
    text cover_url
    boolean public_domain
    tsvector search_document
  }

  EXTERNAL_BOOK_REFS {
    uuid id PK
    uuid book_id FK
    varchar provider
    varchar external_id
    text source_url
  }

  USER_LIBRARY {
    uuid id PK
    uuid user_id FK
    uuid book_id FK
    varchar status
    boolean favorite
    smallint rating
    timestamptz added_at
  }

  READING_PROGRESS {
    uuid id PK
    uuid user_id FK
    uuid book_id FK
    int current_page
    numeric percentage
    date started_at
    date completed_at
    text notes
  }

  DOCUMENTS {
    uuid id PK
    uuid owner_id FK
    uuid book_id FK
    varchar original_name
    varchar content_type
    bigint size_bytes
    varchar storage_key
    varchar status
    text error_message
    int chunk_count
    timestamptz created_at
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
    varchar model
    int input_tokens
    int output_tokens
    bigint latency_ms
    boolean success
    text error_type
    timestamptz created_at
  }
```

## Vector storage

MVP uses Spring AI's PostgreSQL vector store table rather than separate physical vector tables per feature.

Each vector row includes metadata such as:

```json
{
  "type": "document_chunk",
  "ownerId": "uuid",
  "documentId": "uuid",
  "bookId": "uuid-or-null",
  "chunkIndex": 12,
  "sourceName": "clean-architecture.pdf"
}
```

Book metadata vectors use `type=book` and `bookId`. This keeps infrastructure small while allowing portable Spring AI filter expressions.

### Why not a generic Embedding JPA entity?

The vector store already owns persistence/index semantics. Duplicating vectors into a JPA entity would create two sources of truth. Domain tables store business data; `vector_store` stores retrieval documents.

## Important indexes

- `users(email)` unique.
- `books(isbn13)` unique where not null.
- `external_book_refs(provider, external_id)` unique.
- `user_library(user_id, book_id)` unique.
- `reading_progress(user_id, book_id)` unique.
- `books.search_document` GIN.
- `vector_store.embedding` HNSW cosine index.
- JSON metadata GIN index for vector metadata filters.
