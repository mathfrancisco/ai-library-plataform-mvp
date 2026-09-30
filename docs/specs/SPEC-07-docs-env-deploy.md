# SPEC-07 — Docs, env and deploy

**Priority:** P2 · **Depends on:** SPEC-02

## 1. `.env.example` (replace the AI block and fix the user agent)

```env
# Database
POSTGRES_DB=ailibrary
POSTGRES_USER=ailibrary
POSTGRES_PASSWORD=change-me

# Backend auth (replace in production: openssl rand -base64 48)
JWT_SECRET=change-me-with-at-least-32-characters-long-secret
JWT_ACCESS_TTL_MINUTES=15
JWT_REFRESH_TTL_DAYS=30
APP_CORS_ORIGINS=http://localhost:3000

# AI chat - Groq (OpenAI-compatible). Key: https://console.groq.com/keys
AI_ENABLED=false
GROQ_API_KEY=
GROQ_BASE_URL=https://api.groq.com/openai
AI_MODEL_FAST=openai/gpt-oss-20b
AI_MODEL_SMART=openai/gpt-oss-120b
AI_RATE_LIMIT_PER_MINUTE=20

# AI embeddings - local ONNX (no key). Must match the vector column size.
AI_EMBEDDING_DIMENSIONS=384
AI_EMBEDDING_CACHE_DIR=/data/onnx-cache

# External catalogs
OPEN_LIBRARY_USER_AGENT="AILibraryPortfolio/0.1 (contact: you@example.com)"
GOOGLE_BOOKS_API_KEY=

# Frontend
NEXT_PUBLIC_API_URL=http://localhost:8080
```

Remove `OPENAI_*` variables.

## 2. `docker-compose.yml`
Moved to SPEC-08 §1 (ONNX volume, healthcheck, optional `.env`, loopback ports).

## 3. Docs that disagree with the code today

Fix these even if the related code change is not done yet; say "planned" where needed.

| Doc | Claim | Reality | Fix |
|---|---|---|---|
| `02-architecture.md` search sequence | Three branches run in `par` | Sequential in `HybridSearchService` | Update after SPEC-04 §12.8, or change diagram to sequential |
| `03-domain-model.md` indexes | "JSON metadata GIN index" | Only two `->>` expression indexes; column is `JSON` | Update after SPEC-04 §12.2 |
| `03-domain-model.md` ERD | `USER_LIBRARY` has no `updated_at`; `BOOKS` no `created_at`; `READING_PROGRESS` no `updated_at` | V1 has these columns | Regenerate ERD from the migrations |
| `04-api-contract.md` | No request/response bodies, no error codes, no status codes | — | Generate from code with `springdoc-openapi` (`/v3/api-docs`, Swagger UI in dev profile only) and keep the Markdown file as a short index that links to it. Avoids drift. |
| `06-spring-ai-rag.md` | Threshold 0.60, `EmbeddingModel` via OpenAI, "Tool list" of 4 | Thresholds change with MiniLM; `findLocalBooks` added (SPEC-04 §10.4) | Update with SPEC-02 values |
| `06-spring-ai-rag.md` cache key | `operation + entityId + sourceVersion + model + params` | Code hashes the full source text (no version field) | Describe what the code does |
| `07-security.md` | Rate limit "30 AI operations/minute" fixed | Configurable after SPEC-02; plus anonymous search limit (SPEC-04 §12.5) | Update |
| `07-security.md` | No mention of `localStorage` tokens, CSP, deleted-user token window | — | Add a "Known MVP trade-offs" section |
| `00-product-vision.md` | "short/full/TL;DR summary" | `FULL` removed (SPEC-04 §10.7) | Update |
| `08-roadmap.md` | Phases 0–10 read as future work | Most phases are scaffolded; state is in specs | Add a status column (done / partial / broken) and link specs |
| `09-feature-matrix.md` | Every MVP row ✅ | Semantic search, RAG, recommendations are broken until SPEC-04 §0 | Add a status column, same as roadmap |
| `README.md` | "add OPENAI_API_KEY" | Groq + local embeddings after SPEC-02 | Update quick start |

## 4. Docs to update for new behavior
- `06-spring-ai-rag.md`: Groq for chat, local ONNX embeddings, 384 dims, model tiers table, why Groq has no embeddings.
- `05-integrations.md`: add Groq section (limits, models, error mapping).
- `04-api-contract.md`: endpoints and error codes from SPEC-04 (via springdoc, see §3).
- `12-deployment.md`: required env per environment, ONNX cache, prod profile.
- New ADR `ADR-004-groq-chat-local-embeddings.md`.
- `README.md`: quick start with `.env` steps and "get a Groq key" link.

## 5. Deploy target (first cloud deploy)
- Document one concrete path: frontend on Vercel, backend container on a single host (Render/Fly/Railway), Postgres with pgvector (Neon or Supabase).
- List env per service. `NEXT_PUBLIC_API_URL` is a build-time value for the frontend.
- Uploads stay on a persistent volume for MVP; S3 move stays in the scale triggers.

## Acceptance criteria
- A new developer can go from clone to working app using only `README.md` and `.env.example`.
- No doc mentions OpenAI as the active provider.
