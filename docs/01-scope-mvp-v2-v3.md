# Scope: MVP, V2 and V3

## MVP — implemented/scaffolded in this repository

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
- Hybrid merge/ranking with deterministic source weights and deduplication.

### Documents + RAG
- PDF, EPUB, TXT, Markdown upload.
- File type and size validation.
- Apache Tika extraction.
- Spring AI token-aware chunking.
- pgvector storage with owner/document metadata.
- Tenant-filtered retrieval.
- Chat answer + retrieved source snippets.

### AI
- Summary generation with prompt-hash cache.
- Natural-language discovery/query rewriting.
- Spring AI ChatClient.
- Tool calling for read-only personal-library actions plus safe write action to add an existing local book.
- Provider/model/latency/request outcome logging.

### Recommendations
- Rules from library categories/authors/status.
- Vector similarity for local books when embeddings are available.

### Frontend
- Landing, Explore, Search, Book detail, Library, AI assistant, Document chat, Dashboard, Settings.
- Responsive shell and reusable components.

### Platform
- Docker Compose.
- Flyway.
- GitHub Actions.
- Backend unit/integration test foundation.
- Frontend unit test foundation.

## V2

- Multi-book RAG and whole-library RAG.
- Collections and custom reading lists.
- Notes/highlights and RAG over annotations.
- Server-Sent Events streaming chat.
- Google/GitHub OAuth.
- Rich reading sessions/analytics and yearly goals.
- RAG evaluation dataset and automated regression metrics.
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
