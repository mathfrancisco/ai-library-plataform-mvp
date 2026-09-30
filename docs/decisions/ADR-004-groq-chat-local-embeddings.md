# ADR-004: Groq for chat, local ONNX embeddings

**Status:** Accepted (implements SPEC-02)

## Context

The project uses Groq for chat. Groq exposes an OpenAI-compatible Chat Completions API with tool calling and JSON
output, but **no embeddings endpoint**. Semantic search, similar books, vector recommendations and RAG all need
embeddings. CI must not need a paid or keyed provider.

## Decision

- Chat: Groq through the existing Spring AI OpenAI starter with a custom base URL
  (`https://api.groq.com/openai/v1`; the OpenAI SDK used by Spring AI 2.0 needs the `/v1`). Domain code only uses
  `ChatClient` through `AiFacade`.
- Two model tiers per operation: `FAST` (`llama-3.1-8b-instant`) for summaries and discovery, `SMART`
  (`llama-3.3-70b-versatile`) for the assistant and RAG answers; discovery retries once on `SMART` when the fast
  model's structured output fails validation.
- Embeddings: local ONNX `all-MiniLM-L6-v2` (384 dimensions) via `spring-ai-starter-model-transformers`, running in
  the JVM. The DJL PyTorch engine is excluded: the model runs on ONNX Runtime and PyTorch would download hundreds of
  MB of native libraries at runtime.
- `vector_store.embedding` is `vector(384)` (migration V3); a startup check fails fast if `AI_EMBEDDING_DIMENSIONS`
  does not match the column.

## Consequences

- Positive: embeddings are free, need no key, work with `AI_ENABLED=false` and in CI; chat cost is controlled by
  routing most traffic to the fast model.
- Negative: first start downloads the ~90 MB model (cached in the `onnx-cache` volume and in CI);
  embedding costs CPU on the backend; MiniLM is English-centric.
- Similarity thresholds were re-tuned for MiniLM (0.30 for search, RAG and recommendations) and live in
  `app.search`, `app.rag`, `app.recommendation`.

## Alternatives

- Ollama `nomic-embed-text` (768 dims, extra container) — more RAM and one more service.
- Hosted embeddings (Jina, Voyage, Gemini) — a key and network dependency in CI.
- `paraphrase-multilingual-MiniLM-L12-v2` (also 384 dims) if Portuguese content matters; same code, config only.

Switching embedding model later = config change + a migration if the dimension changes + re-index
(`POST /api/admin/books/reindex`, `POST /api/documents/{id}/reingest`).
