# Development Roadmap

## Status (2026-09-30)

Status after the specs in [`docs/specs`](specs/README.md) were implemented. **Done** = implemented and covered by
tests; **partial** = usable, with the listed gap.

| Phase | Status | Notes / specs |
|---|---|---|
| 0 — Foundation | done | SPEC-01, SPEC-03 (formatting), SPEC-08 (compose, healthchecks) |
| 1 — Authentication | done | reuse detection, account deletion, admin emails — SPEC-04 §2 |
| 2 — External catalog | done | Open Library + optional Google Books, dedup by ISBN/title key — SPEC-04 §3 |
| 3 — Personal library | done | rules in `LibraryService` — SPEC-03, SPEC-04 §4 |
| 4 — Hybrid search | done | parallel branches, degraded flags, local embeddings — SPEC-02, SPEC-04 §12 |
| 5 — AI layer | done | Groq tiers, per-user + global limits, request logs — SPEC-02, SPEC-04 §10 |
| 6 — Documents + RAG | done | quotas, context cap, citation check, iterative HNSW scan — SPEC-04 §6–9 |
| 7 — Recommendations | done | threshold 0.30 with MiniLM — SPEC-02 |
| 8 — Dashboard | done | SPEC-05 |
| 9 — Quality | partial | unit + Testcontainers ITs, MSW frontend tests, CodeQL, Trivy, Dependabot (SPEC-06, SPEC-08). Missing: Playwright E2E, provider-backed AI eval workflow |
| 10 — Deployment | partial | images build in CI and prod profile exists; deploy path documented in [12-deployment.md](12-deployment.md). Missing: registry push, object storage, a live environment |

The phase descriptions below are the original plan and are kept for reference.

## Phase 0 — Foundation

**Goal:** runnable monorepo.

- Backend: Boot app, health, error envelope, Flyway.
- Frontend: Next.js shell, API client, query provider.
- DB: PostgreSQL extensions + base tables.
- Tests: context smoke tests.
- Result: `docker compose up --build` boots the stack.

## Phase 1 — Authentication

- Backend: register/login/refresh/logout/me, JWT filter.
- Frontend: login/register forms and token session store.
- DB: users + refresh tokens.
- Tests: auth service and unauthorized/authorized request tests.
- Result: private routes usable.

## Phase 2 — External book catalog

- Backend: provider interface, Open Library, optional Google Books, normalization/dedup.
- Frontend: Explore/search results.
- DB: books + external refs only when imported.
- Tests: provider mapping with fixture JSON.
- Result: user can discover books without manually preloading a catalog.

## Phase 3 — Personal library

- Backend: add/update/remove library items.
- Frontend: library status tabs.
- DB: user_library + reading_progress.
- Tests: owner isolation and status transitions.
- Result: useful non-AI product loop exists.

## Phase 4 — Hybrid search

- Backend: FTS index, book vectors, RRF merge.
- Frontend: mode/filter UI.
- DB: GIN + HNSW indexes.
- Tests: deterministic ranking and dedupe.
- Result: keyword + semantic + external results.

## Phase 5 — AI layer

- Backend: ChatClient gateway, summaries, generation cache, AI request logs, query understanding.
- Frontend: summary/discovery actions.
- DB: ai_generations + ai_request_logs.
- Tests: gateway mocked at service boundary.
- Result: AI is integrated into product flows, not a detached chat button.

## Phase 6 — Document ingestion + RAG

- Backend: upload, Tika, token splitter, embeddings, tenant-filtered retrieval, chat.
- Frontend: document upload/status/chat.
- DB: documents + vector rows.
- Tests: file validation, owner isolation, retrieval filters.
- Result: grounded chat over user-permitted content.

## Phase 7 — Recommendations

- Backend: author/category heuristics + vector similarity.
- Frontend: recommendation rail.
- Tests: excludes already-read/duplicate items where configured.
- Result: personalized discovery.

## Phase 8 — Dashboard

- Backend: aggregate counts/progress.
- Frontend: reading summary cards.
- Tests: aggregation queries.
- Result: personal reading overview.

## Phase 9 — Quality

- Testcontainers PostgreSQL/pgvector.
- RAG fixture/evaluation harness.
- Frontend component tests.
- Static analysis and dependency checks.

## Phase 10 — Deployment

- Container registry.
- Managed PostgreSQL with pgvector.
- Object storage.
- Production CORS, secrets, observability.
- Optional preview environment.
