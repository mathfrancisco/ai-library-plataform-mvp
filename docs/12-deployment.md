# Deployment path

## Local
`docker compose up --build` runs PostgreSQL/pgvector, Spring Boot and Next.js. AI is disabled until `AI_ENABLED=true` and provider credentials are configured.

## First cloud deployment
Keep the same modular monolith:
- Web: managed Node/container host.
- API: one Spring Boot container.
- DB: managed PostgreSQL with pgvector.
- Object storage: move uploads from local volume to S3-compatible storage before horizontal scaling.
- Secrets: platform secret manager; never commit API keys/JWT secret.

## Scale triggers
Add infrastructure only after measurement:
- Redis: when AI generation cache/read pressure justifies it.
- Queue: when ingestion jobs need durable retries/concurrency isolation.
- Dedicated vector DB: only if pgvector capacity/latency is demonstrably insufficient.
- Microservices: only when independent deployment/team ownership becomes a real constraint.
