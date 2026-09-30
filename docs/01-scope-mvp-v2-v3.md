# Scope: MVP, V2 and V3

## MVP — implemented in this repository

Every MVP item below is implemented (status per feature in [09-feature-matrix.md](09-feature-matrix.md)).
Items that changed or moved while implementing [`docs/specs`](specs/README.md):

- **Full summary type removed.** Summaries are `SHORT` and `TLDR`; a "full" summary from a catalog description
  added nothing (SPEC-04 §10.7).
- **Chat provider is Groq, embeddings are local ONNX** (ADR-004). Search, recommendations and document
  ingestion work without an AI key.
- **Search page** is merged into Explore (`/search` redirects to `/explore`).
- **Playwright end-to-end tests** and a provider-backed AI evaluation workflow moved to V2.

### Identity
- Sign up, sign in, access JWT, rotating refresh token, logout.
- USER and ADMIN roles.

### Book catalog
- Open Library provider.
- Optional Google Books provider.
- Normalized catalog DTO and duplicate suppression by ISBN, then normalized title + first author.
- Persist a book only when the user adds/interacts with it.

### Personal library
- Want to read, Reading, Read, Dropped.
- Favorite flag.
- Rating (1–5).
- Reading progress: page, percentage, started/completed timestamps.

### Search
- Local PostgreSQL full-text search.
- External provider search.
- Semantic search over locally indexed book metadata.
- Hybrid merge/ranking with deterministic source weights and deduplication; branches run in parallel and a slow
  branch degrades instead of failing.

### Documents + RAG
- PDF, EPUB, TXT, Markdown upload.
- File type and size validation.
- Apache Tika extraction.
- Spring AI token-aware chunking.
- pgvector storage with owner/document metadata.
- Tenant-filtered retrieval.
- Chat answer + retrieved source snippets with checked `[S1]` citations.
- Book chat over all ready documents linked to one book.
- Per-user quotas (50 documents, 500 MB).

### AI
- Summary generation (`SHORT`, `TLDR`) with prompt-hash cache.
- Natural-language discovery/query rewriting.
- Spring AI ChatClient on Groq with `FAST`/`SMART` model tiers, per-user and global rate limits.
- Tool calling for read-only personal-library actions plus safe write action to add an existing local book.
- Provider/model/latency/request outcome logging.

### Recommendations
- Rules from library categories/authors/status.
- Vector similarity for local books when embeddings are available.

### Frontend
- Landing, Explore (with search and AI discovery), Book detail, Library, AI assistant, Documents + document chat,
  Dashboard, Settings (profile, password, account deletion).
- Loading/empty/error states, toasts, protected routes with `?next=` redirect.
- Responsive shell and reusable components.

### Platform
- Docker Compose (healthchecks, works without `.env`), non-root images.
- Flyway.
- GitHub Actions: tests, lint/format, image build + Trivy scan, compose smoke test; CodeQL; Dependabot.
- Backend unit tests and Testcontainers integration tests (`*IT`).
- Frontend Vitest + MSW tests.
- OpenAPI via springdoc (disabled in `prod`).

## V2

- Multi-book RAG and whole-library RAG.
- Collections and custom reading lists.
- Notes/highlights and RAG over annotations.
- Server-Sent Events streaming chat.
- Google/GitHub OAuth.
- Rich reading sessions/analytics and yearly goals.
- RAG evaluation dataset and automated regression metrics (provider-backed, opt-in workflow).
- Playwright end-to-end tests.
- Refresh token in an `httpOnly` cookie; nonce-based CSP.
- S3-compatible upload storage and shared (Redis) rate limits for more than one backend instance.
- Reranking when retrieval corpus size/quality justifies it.
- Gutenberg OPDS/public-domain importer workflow.

## V3

- MCP server (`search_books`, `search_library`, `get_book`, `ask_book`, `get_notes`, `get_reading_progress`, `recommend_books`).
- Kindle/Goodreads import.
- OCR for scanned documents.
- TTS/audiobooks.
- Flashcards, quizzes and study mode.
- Social/community features.
- Knowledge graph.

## Explicit non-goals for MVP

- Microservices.
- Kafka/RabbitMQ.
- Kubernetes.
- Elasticsearch/OpenSearch.
- Dedicated vector database.
- Custom ML recommender training pipeline.
- Bulk mirroring of Open Library or Google Books.
