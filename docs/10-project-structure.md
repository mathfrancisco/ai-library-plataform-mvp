# Project structure

```text
ai-library-platform/
├── backend/
│   ├── src/main/java/com/ailibrary/
│   │   ├── auth/            # register/login/JWT/refresh
│   │   ├── book/            # local canonical book model
│   │   ├── catalog/         # Open Library + Google Books providers
│   │   ├── library/         # personal shelf
│   │   ├── reading/         # reading progress
│   │   ├── search/          # lexical + semantic + RRF + discovery
│   │   ├── document/        # upload and async ingestion
│   │   ├── rag/             # grounded retrieval/chat
│   │   ├── ai/              # ChatClient, tool calling, cache, logs
│   │   ├── recommendation/  # vector/rule recommendation
│   │   ├── dashboard/       # reading metrics
│   │   └── common/          # security + errors
│   └── src/main/resources/db/migration/
├── frontend/
│   └── src/
│       ├── app/             # Next.js App Router pages
│       ├── components/      # product UI components
│       ├── lib/             # API/auth/query client
│       └── types/           # API models
├── docs/
│   ├── decisions/           # ADRs
│   └── diagrams/            # standalone Mermaid sources
├── .github/workflows/       # CI
├── docker-compose.yml
└── README.md
```

The backend is a domain-oriented modular monolith. Internal packages are intentionally pragmatic: controllers depend on application services; services own authorization-sensitive operations; repositories remain inside their domain. No extra ports/adapters layer is introduced where it would only duplicate Spring abstractions.
