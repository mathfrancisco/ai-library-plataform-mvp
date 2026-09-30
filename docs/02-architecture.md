# Architecture

## Runtime architecture

```mermaid
flowchart TB
  U[Browser / User]
  FE[Next.js 16 App Router\nReact 19 + TanStack Query\n/api proxy]
  API[Spring Boot 4.1 Modular Monolith]

  subgraph Modules[Backend domain modules]
    AUTH[auth]
    BOOK[book]
    CAT[catalog]
    LIB[library]
    READ[reading]
    SEARCH[search]
    DOC[document]
    RAG[rag]
    AI[ai]
    REC[recommendation]
  end

  DB[(PostgreSQL 17)]
  VEC[(pgvector)]
  FS[(Upload volume)]
  OL[Open Library]
  GB[Google Books]
  PG[Project Gutenberg / OPDS\nV2]
  LLM[Groq chat\nvia Spring AI OpenAI starter]
  EMB[Local ONNX embeddings\nall-MiniLM-L6-v2, in-process]

  U -->|same origin /api/*| FE
  FE -->|proxy, REST / JSON + JWT| API
  API --> Modules
  AUTH --> DB
  BOOK --> DB
  LIB --> DB
  READ --> DB
  SEARCH --> DB
  SEARCH --> VEC
  DOC --> FS
  DOC --> VEC
  DOC --> EMB
  SEARCH --> EMB
  RAG --> VEC
  RAG --> LLM
  AI --> LLM
  REC --> VEC
  CAT --> OL
  CAT --> GB
  CAT -. V2 .-> PG
```

## Backend package dependency rule

```mermaid
flowchart LR
  WEB[controllers / DTOs] --> APP[application services]
  APP --> DOMAIN[domain + repositories]
  APP --> INTEGRATIONS[integration adapters]
  INTEGRATIONS --> EXT[external APIs / Spring AI]
  DOMAIN --> DB[(PostgreSQL)]
  COMMON[common/security/config] --> WEB
  COMMON --> APP
```

The project is **not** a textbook clean-architecture implementation. Packages are grouped by business capability; each module owns its controller, service, repository and domain types. Cross-module calls happen through application services, not by reaching directly into another module's tables where avoidable.

## Main request flows

### Catalog search

```mermaid
sequenceDiagram
  actor User
  participant UI as Next.js
  participant Search as SearchController
  participant Hybrid as HybridSearchService
  participant DB as PostgreSQL FTS
  participant Vec as pgvector
  participant Catalog as CatalogService
  participant OL as Open Library

  User->>UI: Search query
  UI->>Search: GET /api/search?q=...
  Search->>Hybrid: search(query)
  par Local lexical
    Hybrid->>DB: full-text search
  and Local semantic
    Hybrid->>Vec: similarity search(type=book)
  and External catalog
    Hybrid->>Catalog: search providers
    Catalog->>OL: /search.json
  end
  Hybrid->>Hybrid: normalize + dedupe + weighted merge
  Hybrid-->>UI: SearchResponse {results, degraded, providers}
```

The three branches run in parallel on virtual threads with per-branch timeouts (lexical 2 s, semantic 2 s,
external 3 s). A branch that fails or times out is listed in `degraded`; each external provider reports `ok` in
`providers`. External results are cached for 5 minutes only when every provider answered.

### Document RAG

```mermaid
sequenceDiagram
  actor User
  participant API as Document/RAG API
  participant Auth as SecurityContext
  participant Vec as pgvector
  participant AI as Spring AI ChatClient

  User->>API: POST /documents/{id}/chat
  API->>Auth: resolve current user
  API->>Vec: similarity search ownerId == user AND documentId == id
  Vec-->>API: top-k chunks
  API->>AI: grounded prompt + chunks
  AI-->>API: answer
  API-->>User: answer + source snippets
```

## Rules that hold across modules

- **No network or model call inside `@Transactional`.** LLM calls, embedding computation and catalog requests run
  outside transactions; writes around them use short transactions (for example catalog import, summaries,
  ingestion). AI request logs are written with `REQUIRES_NEW` so a failed request is still recorded.
- **Every private query is owner-scoped.** Controllers never take an owner id from the client; vector filters for
  private chunks always include `ownerId` (`VectorFilters`).
- **Rules that span modules have one home.** Shelf status ↔ reading progress rules live in `LibraryService`.
- **Errors use one envelope** (`ApiError` with an `ErrorCode`), including 401/403 from the security layer.

## Deployment shape

The MVP has exactly three runtime containers:

1. `frontend`
2. `backend`
3. `postgres` with pgvector

The browser only talks to the frontend origin: `src/proxy.ts` forwards `/api/*` to `API_INTERNAL_URL` at runtime, so
no CORS is needed in the default setup. Uploaded source files and the embedding model cache live in Docker volumes
for local development. Production should replace this with object storage (S3-compatible) without changing the document module contract.
