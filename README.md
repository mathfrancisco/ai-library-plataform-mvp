# AI Library Platform

A portfolio-grade intelligent digital library built with **Java 21, Spring Boot 4, Spring AI, PostgreSQL + pgvector, Next.js and React**.

This is intentionally a **modular monolith**, not a microservice demo. The product combines external book catalogs, a personal library, hybrid search, document ingestion, semantic retrieval, RAG, AI summaries, recommendations and an AI assistant with tool calling.

## What makes it more than CRUD + ChatGPT

- External catalog federation (Open Library first, Google Books optional)
- Local cache only for books the user interacts with
- PostgreSQL Full Text Search + pgvector semantic search
- Natural-language discovery
- PDF/EPUB/TXT/Markdown ingestion with Apache Tika + Spring AI ETL
- Tenant-safe RAG with vector metadata filters
- Chat with one document/book, designed to evolve to multi-book RAG
- AI summaries with deterministic cache keys
- Recommendation rules + vector similarity (no unnecessary ML stack)
- AI request logs for provider/model/tokens/latency/error tracking
- Assistant tool calling for user-library actions

## Current versions (verified 2026-09-29)

| Layer | Version / choice |
|---|---|
| Java | 21 LTS |
| Spring Boot | 4.1.1 |
| Spring AI | 2.0.1 stable |
| PostgreSQL | 17 + pgvector |
| Next.js | 16.3.6 |
| React | 19.3 |
| Tailwind CSS | 4.3 |

> Spring AI 2.1.0-M1 exists, but this repo intentionally uses the latest stable 2.0.1 line.

## Run locally

```bash
cp .env.example .env
# set OPEN_LIBRARY_USER_AGENT contact, then add OPENAI_API_KEY and AI_ENABLED=true for AI/RAG calls

docker compose up --build
```

- Frontend: http://localhost:3000
- Backend: http://localhost:8080
- Health: http://localhost:8080/actuator/health

With `AI_ENABLED=false`, auth, external catalog, lexical search, library and reading tracking still work. Semantic search, ingestion embeddings, recommendations and LLM features require a configured embedding/chat provider.

## Repository map

```text
.
├── backend/                Spring Boot modular monolith
├── frontend/               Next.js App Router UI
├── docs/                   architecture, ADRs, ERD, APIs, roadmap
├── docker-compose.yml      local runtime
├── .env.example
└── .github/workflows/ci.yml
```

## Delivery phases

**MVP:** auth → external catalog → personal library → hybrid search → document upload → embeddings/RAG → book/document chat → summaries → simple recommendations → dashboard baseline.

**V2:** multi-book RAG, richer notes/highlights, collections, advanced reading analytics, streaming chat, Google/GitHub OAuth.

**V3:** MCP server, importers, community/reading clubs, TTS/audiobooks, OCR, flashcards/quizzes, knowledge graph.

See [`docs/01-scope-mvp-v2-v3.md`](docs/01-scope-mvp-v2-v3.md) and [`docs/08-roadmap.md`](docs/08-roadmap.md).

## Architecture diagrams

GitHub renders Mermaid directly. Start with:

- [`docs/02-architecture.md`](docs/02-architecture.md)
- [`docs/03-domain-model.md`](docs/03-domain-model.md)
- [`docs/06-spring-ai-rag.md`](docs/06-spring-ai-rag.md)
- [`docs/diagrams/feature-map.mmd`](docs/diagrams/feature-map.mmd)
- [`docs/09-feature-matrix.md`](docs/09-feature-matrix.md)
- [`docs/10-project-structure.md`](docs/10-project-structure.md)

## External API policy

The app does **not** bulk-import protected book content. Full text enters the RAG pipeline only when the source allows it (for example public-domain content) or the user uploads a file they are entitled to use. Open Library is treated as a low-volume discovery/lookup provider and responses are cached when relevant.

## Branding ideas

The repository intentionally keeps a descriptive name. Product-facing candidates for later rebranding: **ShelfMind**, **LibrisAI**, **Readora**, **KnowShelf**, **PagePilot AI**.
