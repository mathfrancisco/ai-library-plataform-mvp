# AI Library Platform

A portfolio-grade intelligent digital library built with **Java 21, Spring Boot 4, Spring AI, PostgreSQL + pgvector, Next.js and React**.

This is intentionally a **modular monolith**, not a microservice demo. The product combines external book catalogs, a personal library, hybrid search, document ingestion, semantic retrieval, RAG, AI summaries, recommendations and an AI assistant with tool calling.

## What makes it more than CRUD + ChatGPT

- External catalog federation (Open Library first, Google Books optional), deduplicated by ISBN and normalized title + author
- Local cache only for books the user interacts with
- PostgreSQL full-text search + pgvector semantic search, merged with weighted reciprocal-rank fusion (branches run in parallel)
- Natural-language discovery with typed structured output
- PDF/EPUB/TXT/Markdown ingestion with Apache Tika + Spring AI token splitting
- Tenant-safe RAG: every private vector query is filtered by owner, with HNSW iterative scans so small tenants keep recall
- Chat with one document or with all your documents for a book, with cited sources
- AI summaries cached by a deterministic key; request logs with model, tokens, latency and failures
- Recommendations from author/category rules + vector similarity, with reasons
- Assistant with tool calling over your own shelf only

## Stack (verified 2026-09-30)

| Layer | Choice |
|---|---|
| Java | 21 LTS |
| Spring Boot | 4.1.1 |
| Spring AI | 2.0.1 |
| Chat | Groq (OpenAI-compatible API) — fast and smart model tiers |
| Embeddings | Local ONNX `all-MiniLM-L6-v2` (384 dims), no key |
| Database | PostgreSQL 17 + pgvector ≥ 0.8 |
| Frontend | Next.js 16, React 19, TanStack Query, Tailwind 4 |

See [ADR-004](docs/decisions/ADR-004-groq-chat-local-embeddings.md) for why chat and embeddings use different providers.

## Run locally

```bash
docker compose up --build        # works on a fresh clone, no .env needed
```

- App: http://localhost:3000 (the browser calls `/api/*` on this origin; Next proxies to the backend)
- API docs: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

Everything except the chat-based features works out of the box: sign-up, catalog search, shelf and progress,
semantic search, document upload and indexing (local embeddings). The first start downloads the embedding model
(~90 MB) into the `onnx-cache` volume.

To enable summaries, discovery, the assistant and RAG answers:

```bash
cp .env.example .env
# set AI_ENABLED=true and GROQ_API_KEY (get one at https://console.groq.com/keys)
# set OPEN_LIBRARY_USER_AGENT to include your contact
docker compose up --build
```

With `AI_ENABLED=false`, AI endpoints answer `503 AI_DISABLED` and the UI explains it.

### Develop on the host

```bash
make dev-db                                   # PostgreSQL + pgvector only
cd backend && ./mvnw spring-boot:run          # http://localhost:8080
cd frontend && npm ci && npm run dev          # http://localhost:3000
```

`make verify` runs what CI runs (backend integration tests need Docker, or point `TEST_DATABASE_URL` at a pgvector ≥ 0.8 database).

## Repository map

```text
.
├── backend/                Spring Boot modular monolith (./mvnw)
├── frontend/               Next.js App Router UI
├── docs/                   product, architecture, ADRs, API, specs
├── docker-compose.yml      full local stack
├── docker-compose.dev.yml  database only
└── .github/workflows/      CI (tests, image builds, compose smoke test) and CodeQL
```

## Status and roadmap

The MVP scope in [`docs/01-scope-mvp-v2-v3.md`](docs/01-scope-mvp-v2-v3.md) is implemented; the completion work is
tracked in [`docs/specs/`](docs/specs/README.md). V2/V3 items are in [`docs/08-roadmap.md`](docs/08-roadmap.md) and
[`docs/09-feature-matrix.md`](docs/09-feature-matrix.md).

## Documentation

- [`docs/02-architecture.md`](docs/02-architecture.md) — modules, request flows, deployment shape
- [`docs/03-domain-model.md`](docs/03-domain-model.md) — ERD from the migrations, vector storage
- [`docs/04-api-contract.md`](docs/04-api-contract.md) — endpoint index, error codes (full schema: `/v3/api-docs`)
- [`docs/06-spring-ai-rag.md`](docs/06-spring-ai-rag.md) — Groq, embeddings, RAG, tools
- [`docs/07-security.md`](docs/07-security.md) — auth, tenant isolation, limits, known trade-offs
- [`docs/12-deployment.md`](docs/12-deployment.md) — environments and first cloud deploy

## External API policy

The app does **not** bulk-import protected book content. Full text enters the RAG pipeline only when the source allows it (for example public-domain content) or the user uploads a file they are entitled to use. Open Library is treated as a low-volume discovery/lookup provider and responses are cached when relevant.
