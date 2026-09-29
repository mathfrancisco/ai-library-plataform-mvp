# SPEC-06 — Test coverage

**Priority:** P1 · **Depends on:** SPEC-02, SPEC-04

Current tests: `JwtServiceTest`, `CatalogFingerprintTest`, `SchemaIntegrationTest`, one context test, one `BookCard` test. The testing doc (`11-testing-and-quality.md`) promises more. No test may call Groq; use a stub `ChatModel` bean in tests.

## Backend (JUnit 5 + Testcontainers pgvector)

Shared base: `AbstractIntegrationTest` with one reused `pgvector/pgvector:pg17` container, `@SpringBootTest(webEnvironment = RANDOM_PORT)`, stub `ChatModel` returning fixed text/tool calls.

| Test | Proves |
|---|---|
| `BookVectorIndexerIT` | indexing a book writes a vector row and `similaritySearch` finds it (regression for the non-UUID id bug) |
| `ErrorContractIT` | anonymous → 401 `UNAUTHORIZED`; bad UUID, bad enum, oversized upload, unknown error all return `ApiError` with stable codes |
| `AuthFlowIT` | register → login → refresh rotates token → old refresh token rejected → logout revokes |
| `SecurityBoundaryIT` | private endpoints return 401 without token; USER cannot call ADMIN endpoints |
| `LibraryOwnershipIT` | user B cannot read/update/delete user A's library item or progress |
| `DocumentOwnershipIT` | user B cannot list, read, chat with or delete user A's document |
| `RagIsolationIT` | two users upload similar text; retrieval for A never returns B's chunks (uses real local embeddings) |
| `UploadValidationIT` | wrong magic bytes, empty file, oversized file are rejected with the right codes |
| `IngestionFailureIT` | corrupt PDF ends in `FAILED` with `failureReason` |
| `AiDisabledIT` | `AI_ENABLED=false` → AI endpoints return `AI_DISABLED` |
| `ModelRoutingTest` (unit) | summary uses FAST, assistant uses SMART, discovery falls back once |
| `HybridSearchServiceTest` (unit) | RRF merge order and dedup by ISBN / title+author |
| `AiRateLimiterTest` (unit) | limit from config, window reset |
| `FilteredRetrievalRecallIT` | 2,000 chunks of user B + 5 chunks of user A; user A's document query returns user A's chunks (regression for SPEC-04 §12.1) |
| `AiFailureLoggingIT` | stub `ChatModel` throws → `ai_request_logs` has one row with `success=false` after the request (regression for SPEC-04 §12.3) |
| `CatalogServiceTest` (unit) | provider throws → result not cached; next call hits the provider again (SPEC-04 §12.4) |
| `RequestRateLimitIT` | 31st anonymous search in a minute from one IP → `429 RATE_LIMITED` (SPEC-04 §12.5) |
| `ProviderMappingTest` (unit) | Open Library and Google Books JSON fixtures in `src/test/resources/fixtures/` map to the expected `CatalogBook` (promised by roadmap Phase 2, missing today); Google `http://` thumbnail becomes `https://` |

### Test infrastructure rules
- Stub AI with a `@TestConfiguration` that provides a fake `ChatModel`; do not mock `AiFacade`, so logging and rate limiting are exercised.
- Local ONNX embeddings are real in ITs (no key needed). Cache the model in CI with `actions/cache` on the transformer cache dir so the ~90 MB download happens once.
- Use one shared container (`static` + Testcontainers `@ServiceConnection`) for all ITs; do not start one per class.
- Split `*Test` (surefire, no Docker) from `*IT` (failsafe, Docker) so `./mvnw test` stays fast locally.

## Frontend (Vitest + Testing Library)

| Test | Proves |
|---|---|
| `api.test.ts` | single-flight refresh; 401 after failed refresh clears tokens |
| `RequireAuth.test.tsx` | redirect with `next` param |
| `settings.test.tsx` | profile update, password form validation, delete needs typed confirmation |
| `documents.test.tsx` | shows FAILED reason; stops polling when nothing is processing |
| `explore.test.tsx` | pending state is per card; external card click imports and navigates |
| `Nav.test.tsx` | mobile menu opens and lists all links; signed-in state shows user name |

Mock HTTP with `msw` (one handler set shared by all tests) instead of stubbing `fetch` per test.

### End-to-end smoke (optional for MVP, recommended before first deploy)
One Playwright test against `docker compose up` with `AI_ENABLED=false`: register → search → add book → set status/progress → upload TXT → status reaches `READY`. Runs in a separate CI job on `main` only.

## Optional opt-in workflow
`.github/workflows/ai-eval.yml`, manual trigger only, uses `GROQ_API_KEY` secret, runs a small RAG eval (recall@k + citation presence) on a public-domain text. Not required for MVP done.

## Acceptance criteria
- All tests run in CI without secrets.
- Backend CI time stays under 10 minutes.
