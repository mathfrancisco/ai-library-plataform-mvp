# Testing and quality strategy

## Backend
- Unit tests: token/security behavior, normalization/dedup (`BookFingerprintTest`, `CatalogServiceTest`), provider mapping
  from fixture JSON (`CatalogProviderMappingTest`), RRF fusion (`HybridSearchFusionTest`), upload validation, ingestion
  chunk metadata, reading/library status transitions, recommendation rules, dashboard aggregation, AI cache keys and rate limiting.
- Repository/integration tests extend `PostgresIntegrationTest`: PostgreSQL + pgvector through Testcontainers, or an
  existing database when `TEST_DATABASE_URL` (+ `TEST_DATABASE_USERNAME`/`TEST_DATABASE_PASSWORD`) is set. They are
  skipped when neither is available.
  - `SchemaIntegrationTest` verifies Flyway, vector/FTS indexes and the generated `search_document`.
  - `ApiIntegrationTest` covers auth boundaries, owner isolation across library/reading/dashboard and error codes over HTTP.
  - `PgVectorTenantIsolationTest` runs the application's metadata filters through the real pgvector store.
- RAG tests: `RagRetrievalEvaluationTest` uses a fixed corpus with question → expected chunk mappings, asserts recall@k
  and that no other owner's chunks are ever retrieved. It uses a deterministic hashing embedding, so it needs no provider.

## Frontend
- Vitest + Testing Library for UI behavior.
- Route-level smoke tests for critical screens (library, dashboard, settings) with a stubbed `fetch`.
- API client tests: error envelope codes, single-flight refresh-token rotation, session clearing.
- Add Playwright in V2 when login/upload/RAG workflows stabilize.

## AI evaluation dataset
Create a small legal/public-domain corpus with question → expected source chunk mappings. Track:
1. recall@k for expected chunks;
2. answer citation presence;
3. groundedness against retrieved context;
4. p50/p95 latency;
5. input/output token use.

No CI test should require a paid LLM by default. Provider-backed eval runs belong in an opt-in workflow with secrets.
