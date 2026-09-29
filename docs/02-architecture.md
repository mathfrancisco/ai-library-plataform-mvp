# Architecture

## Runtime architecture

```mermaid
flowchart TB
  U[Browser / User]
  FE[Next.js 16 App Router\nReact 19 + TanStack Query]
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
  LLM[LLM / Embedding provider\nvia Spring AI]

  U --> FE
  FE -->|REST / JSON + JWT| API
  API --> Modules
  AUTH --> DB
  BOOK --> DB
  LIB --> DB
  READ --> DB
  SEARCH --> DB
  SEARCH --> VEC
  DOC --> FS
  DOC --> VEC
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
  Hybrid-->>UI: ranked SearchResult[]
```

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

## Deployment shape

The MVP has exactly three runtime containers:

1. `frontend`
2. `backend`
3. `postgres` with pgvector

Uploaded source files live in a Docker volume for local development. Production should replace this with object storage (S3-compatible) without changing the document module contract.
