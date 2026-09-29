# Testing and quality strategy

## Backend
- Unit tests: token/security behavior, normalization, ranking helpers, domain services.
- Repository/integration tests: PostgreSQL + pgvector through Testcontainers (`SchemaIntegrationTest` verifies Flyway + vector schema).
- HTTP integration tests: auth boundaries, upload ownership, library operations.
- RAG tests: fixed corpus + expected retrieval source IDs, groundedness checks, no cross-owner retrieval.

## Frontend
- Vitest + Testing Library for UI behavior.
- Route-level smoke tests for critical screens.
- Add Playwright in V2 when login/upload/RAG workflows stabilize.

## AI evaluation dataset
Create a small legal/public-domain corpus with question → expected source chunk mappings. Track:
1. recall@k for expected chunks;
2. answer citation presence;
3. groundedness against retrieved context;
4. p50/p95 latency;
5. input/output token use.

No CI test should require a paid LLM by default. Provider-backed eval runs belong in an opt-in workflow with secrets.
