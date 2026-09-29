# Feature matrix

| Capability | MVP | V2 | V3 | Main module |
|---|:---:|:---:|:---:|---|
| External catalog federation | ✅ | | | `catalog` |
| Manual book registration | ✅ | | | `book` |
| Personal library/status/favorites/rating | ✅ | | | `library` |
| Reading progress | ✅ | | | `reading` |
| PostgreSQL full-text search | ✅ | | | `search` |
| Semantic book search | ✅ | | | `search` + pgvector |
| Hybrid/RRF search | ✅ | | | `search` |
| Natural-language discovery | ✅ | | | `search` + Spring AI |
| PDF/EPUB/TXT/MD ingestion | ✅ | | | `document` |
| Owner-isolated document vectors | ✅ | | | `document` + `rag` |
| Single-document grounded RAG | ✅ | | | `rag` |
| AI catalog-description summaries + cache | ✅ | | | `ai` |
| Library assistant with Tool Calling | ✅ | | | `ai` |
| Rule/vector recommendations | ✅ | | | `recommendation` |
| AI request telemetry | ✅ | | | `ai_request_logs` |
| Reading dashboard | ✅ | | | `dashboard` |
| Multi-book RAG | | ✅ | | `rag` |
| Notes/highlights semantic search | | ✅ | | future `notes` |
| Custom collections | | ✅ | | future `collection` |
| Social login | | ✅ | | `auth` |
| Redis AI cache | | ✅ | | infrastructure |
| Reranker / richer RAG eval UI | | ✅ | | `rag-eval` |
| Goodreads/Kindle import | | | ✅ | integrations |
| MCP server | | | ✅ | adapters over application services |
| TTS/OCR/mobile/social features | | | ✅ | future adapters |
