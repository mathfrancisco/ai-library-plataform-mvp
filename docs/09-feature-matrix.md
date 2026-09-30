# Feature matrix

Status as of 2026-09-30, after [`docs/specs`](specs/README.md). "Needs `AI_ENABLED`" means the feature calls
Groq; everything else works without an AI key.

| Capability | MVP | V2 | V3 | Main module | Status |
|---|:---:|:---:|:---:|---|---|
| External catalog federation | ✅ | | | `catalog` | done |
| Manual book registration | ✅ | | | `book` | done |
| Personal library/status/favorites/rating | ✅ | | | `library` | done |
| Reading progress | ✅ | | | `reading` | done |
| PostgreSQL full-text search | ✅ | | | `search` | done |
| Semantic book search | ✅ | | | `search` + pgvector | done (local ONNX embeddings) |
| Hybrid/RRF search | ✅ | | | `search` | done (parallel branches) |
| Natural-language discovery | ✅ | | | `search` + Spring AI | done (needs `AI_ENABLED`) |
| PDF/EPUB/TXT/MD ingestion | ✅ | | | `document` | done |
| Owner-isolated document vectors | ✅ | | | `document` + `rag` | done |
| Single-document grounded RAG | ✅ | | | `rag` | done (needs `AI_ENABLED`); book chat over a book's documents too |
| AI catalog-description summaries + cache | ✅ | | | `ai` | done (`SHORT`, `TLDR`) |
| Library assistant with Tool Calling | ✅ | | | `ai` | done (5 tools incl. `findLocalBooks`) |
| Rule/vector recommendations | ✅ | | | `recommendation` | done |
| AI request telemetry | ✅ | | | `ai_request_logs` | done (`ai_request_logs`; per-user usage at `GET /api/ai/usage`) |
| Reading dashboard | ✅ | | | `dashboard` | done |
| Multi-book RAG | | ✅ | | `rag` | planned |
| Notes/highlights semantic search | | ✅ | | future `notes` | planned |
| Custom collections | | ✅ | | future `collection` | planned |
| Social login | | ✅ | | `auth` | planned |
| Redis AI cache | | ✅ | | infrastructure | planned |
| Reranker / richer RAG eval UI | | ✅ | | `rag-eval` | planned |
| Goodreads/Kindle import | | | ✅ | integrations | planned |
| MCP server | | | ✅ | adapters over application services | planned |
| TTS/OCR/mobile/social features | | | ✅ | future adapters | planned |
