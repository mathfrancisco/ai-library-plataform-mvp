# SPEC-02 — Groq chat + local embeddings

**Priority:** P0 · **Depends on:** SPEC-01

## Problem

The AI layer is configured for OpenAI (`spring-ai-starter-model-openai`, `gpt-4.1-mini`, `text-embedding-3-small`, `VECTOR(1536)`). The project will use **Groq** (https://console.groq.com), which:

- exposes an OpenAI-compatible Chat Completions API at `https://api.groq.com/openai/v1`;
- supports tool calling and JSON output on its larger models;
- has **no embeddings endpoint**;
- offers several models with different speed/quality/limits.

## Decisions

| Concern | Decision | Reason |
|---|---|---|
| Chat provider | Groq through the existing Spring AI OpenAI starter, custom `base-url` | No new SDK; domain code already uses `ChatClient` only |
| Embeddings | Local ONNX via `spring-ai-starter-model-transformers`, model `all-MiniLM-L6-v2` (384 dims) | Free, no key, runs in the JVM, works in CI |
| Vector size | `VECTOR(384)` | Must match the embedding model |
| Models | Two tiers: `fast` and `smart`, selected per operation | Use cheap model where quality matters less |

Alternative embeddings (documented, not implemented): Ollama `nomic-embed-text` (768 dims, extra container) or a hosted API (Jina/Voyage/Gemini). Switching later is a config + migration change only.

## Changes

### Dependencies (`backend/pom.xml`)
- Keep `spring-ai-starter-model-openai` (used for Groq chat).
- Add `spring-ai-starter-model-transformers`.

### Configuration (`application.yml`)
```yaml
spring:
  ai:
    model:
      chat: openai          # Groq via OpenAI-compatible API
      embedding: transformers
    openai:
      api-key: ${GROQ_API_KEY:disabled}
      base-url: ${GROQ_BASE_URL:https://api.groq.com/openai}
      chat:
        options:
          model: ${AI_MODEL_SMART:openai/gpt-oss-120b}
          temperature: 0.2
    embedding:
      transformer:
        cache:
          directory: ${AI_EMBEDDING_CACHE_DIR:/tmp/spring-ai-onnx}
    vectorstore:
      pgvector:
        dimensions: 384
app:
  ai:
    enabled: ${AI_ENABLED:false}
    provider: groq
    models:
      fast: ${AI_MODEL_FAST:openai/gpt-oss-20b}
      smart: ${AI_MODEL_SMART:openai/gpt-oss-120b}
    rate-limit-per-minute: ${AI_RATE_LIMIT_PER_MINUTE:20}
```
Verify exact property names for Spring AI 2.0.1 with Context7 before coding (`spring.ai.model.*` selectors and transformer cache keys).

### Database
- New migration `V2__embedding_384.sql`: drop HNSW index, `TRUNCATE vector_store`, `ALTER COLUMN embedding TYPE vector(384)`, recreate index. Never edit `V1`.
- Same migration (table is empty, so it is free): `ALTER COLUMN metadata TYPE jsonb`, replace the two `->>` expression indexes with `GIN (metadata jsonb_path_ops)`, and enable filtered HNSW iterative scan. See SPEC-04 §12.1–12.2.
- Make the dimension a single source of truth: `AI_EMBEDDING_DIMENSIONS` drives `spring.ai.vectorstore.pgvector.dimensions`, and a startup check compares it with `SELECT atttypmod FROM pg_attribute WHERE attrelid = 'vector_store'::regclass AND attname = 'embedding'`. Fail fast on mismatch instead of failing on the first insert.
- After deploy, books are re-indexed on next write; documents must be re-uploaded or re-ingested (add an admin-only `POST /api/admin/reindex` only if cheap; otherwise note it in the release notes).

### Model routing
- `AiProperties` gets `models.fast`, `models.smart`, `rateLimitPerMinute`.
- `AiFacade.complete/structured/tools` take a `ModelTier` enum (`FAST`, `SMART`) and set `OpenAiChatOptions.model(...)` per call.
- Mapping:

| Operation | Tier |
|---|---|
| Book summary | FAST |
| Discovery / query rewrite (structured output) | FAST, fall back to SMART once if schema validation fails |
| Assistant with tool calling | SMART |
| Document / book RAG answer | SMART |

- `AiRequestLog` stores the actual model used (already has a model column; fill it from the per-call option, not the global config).
- Cache key for summaries already includes model; keep it.

### Rate limiting
- `AiRateLimiter` reads `rateLimitPerMinute` from `AiProperties` instead of the hard-coded `30`.
- Map Groq `429` to `RateLimitException` so the user gets the same message as the local limiter.

## Acceptance criteria

- With `AI_ENABLED=true` and a valid `GROQ_API_KEY`: summary, discovery, assistant (including a tool call) and document RAG all return answers; `ai_request_log` shows both models used.
- Document upload reaches `READY` with no external embedding call.
- With `AI_ENABLED=false` and no key: app starts, embeddings still work locally, chat endpoints return the existing "AI disabled" error.
- CI does not need `GROQ_API_KEY`.
- ONNX model download is cached in the Docker image or a volume, not fetched on every start.

## Risks

- First start downloads the ONNX model (~90 MB). Mitigate with a Docker build step or named volume.
- MiniLM is English-centric. If Portuguese content matters, evaluate `paraphrase-multilingual-MiniLM-L12-v2` (also 384 dims) with the same code.
- Groq free-tier limits are per model; the fast tier keeps most traffic off the smart model.
