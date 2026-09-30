# Testing and quality strategy

## Commands

| Command | Runs |
|---|---|
| `make test` / `cd backend && ./mvnw verify` | unit tests (Surefire, `*Test`), then integration tests (Failsafe, `*IT`), Spotless check |
| `cd backend && ./mvnw test` | unit tests only; no database needed |
| `cd frontend && npm run test` | Vitest |
| `cd frontend && npm run lint && npm run format:check` | ESLint + Prettier |

CI (`.github/workflows/ci.yml`) runs all of these, builds both Docker images, checks they run as non-root, scans
them with Trivy (report only) and starts `docker compose` from a clean clone without `.env` until the stack is
healthy. CodeQL runs separately (`codeql.yml`).

## Backend

### Unit tests (`*Test`, no database, no provider)
- Auth and tokens: `AuthServiceTest`, `JwtServiceTest`.
- Catalog: `BookFingerprintTest`, `CatalogServiceTest`, `CatalogProviderMappingTest` (fixture JSON).
- Library and reading: `LibraryServiceTest`, `ReadingProgressServiceTest`.
- Search: `HybridSearchFusionTest` (RRF), `SearchFilterTest`, `DiscoveryRoutingTest` (FAST → SMART retry).
- Documents: `DocumentServiceTest` (validation, quotas), `TikaExtractionTest`, `DocumentIngestionProcessorTest`,
  `DocumentIngestionWorkerTest`.
- AI: `ModelRoutingTest`, `AiRateLimiterTest`, `BookSummaryServiceTest` (cache key), `LibraryAssistantToolsTest`.
- RAG: `GroundedAnswerRulesTest` (context cap, citation check), `VectorFiltersTest`, `RagRetrievalEvaluationTest`
  (fixed corpus, recall@k, no cross-owner chunks; deterministic hashing embedding).
- `RecommendationServiceTest`, `DashboardServiceTest`.

### Integration tests (`*IT`, Failsafe)
They extend `PostgresIntegrationTest`: PostgreSQL + pgvector through Testcontainers, or an existing database when
`TEST_DATABASE_URL` (+ `TEST_DATABASE_USERNAME`/`TEST_DATABASE_PASSWORD`) is set. Locally they are skipped when
neither is available; CI passes `-Dtestcontainers.required=true` so a missing Docker fails the build instead.

| IT | Covers |
|---|---|
| `SchemaIT` | Flyway, vector/FTS indexes, generated `search_document` |
| `ApiIT` | auth boundaries, owner isolation across library/reading/documents, specific error codes, admin role |
| `AuthFlowIT` | refresh rotation, reuse detection, profile update, password change, account deletion |
| `ErrorContractIT` | error envelope (`code`, `message`, `path`, `requestId`) for 400 (bad UUID/enum), 401 and 413 |
| `RequestRateLimitIT` | anonymous search limit per IP → `429 RATE_LIMITED` |
| `DocumentIT` | upload → ingestion `READY`, isolation from other users, wrong magic bytes, empty and corrupt files |
| `RagIsolationIT` | the application's metadata filters through the real pgvector store |
| `FilteredRetrievalRecallIT` | tenant-filtered HNSW search still returns top-k (iterative scan) |
| `BookVectorIndexerIT` | an indexed book is found by semantic search |
| `AiFailureLoggingIT` | provider failures are mapped and still logged in `ai_request_logs` |

No test calls Groq (`app.ai.enabled=false`). `RagIsolationIT`, `FilteredRetrievalRecallIT` and
`RagRetrievalEvaluationTest` use the deterministic `HashingEmbeddingModel`; `DocumentIT` and `BookVectorIndexerIT`
run the real local ONNX model, which is downloaded from Hugging Face on first use and cached in
`AI_EMBEDDING_CACHE_DIR`.

## Frontend
- Vitest + Testing Library; API calls are mocked at the network level with **MSW** (`src/test/server.ts`).
- Components: `Nav`, `BookCard`, `RecommendationCard`, `RequireAuth`.
- Pages: library, dashboard, settings, explore, documents.
- API client (`lib/__tests__/api.test.ts`): error envelope codes, single-flight refresh-token rotation, session
  clearing and redirect to `/login?next=`.
- Playwright end-to-end tests are a V2 item; the flow is checked by hand before release (register → add book →
  progress → upload → search → dashboard).

## AI evaluation dataset
Create a small legal/public-domain corpus with question → expected source chunk mappings. Track:
1. recall@k for expected chunks;
2. answer citation presence;
3. groundedness against retrieved context;
4. p50/p95 latency;
5. input/output token use.

No CI test requires an LLM. Provider-backed eval runs belong in an opt-in workflow with secrets (not added yet).
