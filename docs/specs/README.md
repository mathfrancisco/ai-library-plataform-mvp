# MVP completion specs

These specs close the gap between the documented MVP scope (`../01-scope-mvp-v2-v3.md`) and the current code. They are ordered by dependency: each spec lists what it needs first.

| # | Spec | Area | Depends on | Priority |
|---|---|---|---|---|
| 01 | [CI and build fixes](SPEC-01-ci-build-fixes.md) | platform | — | P0 (blocker) |
| 02 | [Groq chat + local embeddings](SPEC-02-groq-and-local-embeddings.md) | backend AI | 01 | P0 |
| 03 | [Code readability pass](SPEC-03-code-readability.md) | backend + frontend | 01 | P1 |
| 04 | [Backend module fixes](SPEC-04-backend-hardening.md) | backend | 01 (02 for AI items) | P0 for §0–1, else P1 |
| 05 | [Frontend page fixes + completion](SPEC-05-frontend-completion.md) | frontend | 03, 04 | P0 for §0–1, else P1 |
| 06 | [Test coverage](SPEC-06-test-coverage.md) | backend + frontend | 02, 04 | P1 |
| 07 | [Docs, env and deploy](SPEC-07-docs-env-deploy.md) | docs/platform | 02 | P2 (§3 doc corrections: P1) |
| 08 | [Infra, containers and CI](SPEC-08-infra-and-ops.md) | platform | 01 | P1 (§1.1 P0) |

## Execution order

Specs overlap in files. Follow this order to avoid merge conflicts and rework:

1. **SPEC-01** — green CI. Nothing else merges on a red build.
2. **SPEC-03 tooling + one `style: format codebase` commit** — do it before any logic change, because every file is touched. Structural cleanup (component split, hooks) waits until step 5.
3. **SPEC-02 + SPEC-04 §0 + §12.1–12.2** — one migration (`V2`) for embedding size, JSONB metadata and HNSW iterative scan. After this, vector features work for the first time. Prove with `BookVectorIndexerIT` and `FilteredRetrievalRecallIT`.
4. **SPEC-04 §1 + §12.7** (error contract, 401) together with **SPEC-05 §1.1** (single-flight refresh). Frontend and backend must change in the same PR, or sessions break.
5. **SPEC-04 remaining 🟠**, then **SPEC-05** page by page, each backend endpoint change shipped with its UI.
6. **SPEC-08** and **SPEC-06** in parallel (disjoint files).
7. **SPEC-07** last; §3 doc corrections can land any time.

Rule for every PR: the tests named in SPEC-06 for that item are in the same PR.

## Definition of done for the MVP

1. CI is green on `main` (backend `mvn verify`, frontend lint/test/build, `docker compose build`).
2. `docker compose up --build` with a filled `.env` gives a working app: register, search, add book, track progress, upload a document, ask it a question, use the assistant.
3. With `AI_ENABLED=false` every non-AI feature still works and AI endpoints return a clear `503`-style error.
4. No paid or keyed provider is needed for CI.
5. Every scope item in `01-scope-mvp-v2-v3.md` is either implemented or moved to V2 with a note.
6. A fresh clone with **no `.env`** starts with `docker compose up --build` (SPEC-08 §1.1); containers do not run as root.
7. With two users and a large document from user B, user A's document chat still returns user A's sources (SPEC-04 §12.1).
8. No doc in `docs/` states behavior that the code does not have (SPEC-07 §3).

## Findings that motivated these specs

Critical (feature is broken today):
- **Vector IDs are not UUIDs** (`"book-<id>"`, `"doc-<id>-<n>"`) but `vector_store.id` is `UUID`. Every book index and every document ingestion fails → semantic search, similar books, vector recommendations, document chat and book chat do not work. See SPEC-04 §0.
- Anonymous/expired requests get `403`, but the frontend refreshes only on `401` → token refresh never runs. See SPEC-04 §1.
- `frontend/public/` is missing → frontend Docker image does not build. See SPEC-01.
- Parallel `401`s rotate the refresh token several times → user is logged out. See SPEC-05 §1.1.
- No mobile navigation (nav hidden under 900 px). See SPEC-05 §1.6.
- MVP scope items missing in the UI: status change, favorite, rating, loading existing progress, Settings page.

Other:
- CI fails: YAML parse error in `application.yml` (unquoted `: ` in a default value) and missing `frontend/package-lock.json`.
- Groq has an OpenAI-compatible chat API but **no embeddings API**. RAG, semantic search and vector recommendations need a separate embedding source.
- Only one chat model is configurable; Groq offers several with different cost/latency.
- Many frontend files and several backend classes are minified into single lines (up to 7 lines over 200 chars per file). This hurts review and maintenance.
- `Settings` page is in scope but missing.
- Tests cover only JWT, fingerprinting and schema; no HTTP, ownership or RAG isolation tests.

Added in the second review (code re-checked):
- **Filtered HNSW search loses results**: pgvector filters after the index scan (`ef_search` 40), so tenant-filtered RAG returns nothing once other users have many chunks. See SPEC-04 §12.1.
- `vector_store.metadata` is `JSON`; Spring AI filters cast to `jsonb`, so the V1 expression indexes are never used. SPEC-04 §12.2.
- `BookSummaryService` calls the LLM inside `@Transactional`: holds a DB connection during the call, and failure logs are rolled back. SPEC-04 §12.3.
- Catalog search caches provider failures (empty results) for 5 min. SPEC-04 §12.4.
- Anonymous search has no rate limit; each call runs an embedding and external requests. SPEC-04 §12.5.
- Chat/question/notes inputs have no size limit. SPEC-04 §12.6.
- Hybrid search branches run in sequence; docs say parallel. SPEC-04 §12.8.
- Compose needs `.env` to start, Postgres is exposed on all interfaces, both containers run as root, no backend healthcheck. SPEC-08.
- `/actuator/metrics` is readable by any signed-in user. SPEC-08 §6.1.
- No CSP or security headers while tokens are in `localStorage`. SPEC-05 §5.
- Testcontainers is pinned to 1.21.3 against Spring Boot 4 (manages 2.x), and ITs pass silently without Docker. SPEC-01 §6–7.
- Several docs describe behavior the code does not have (parallel search, GIN index, ERD columns, feature matrix all ✅). SPEC-07 §3.
