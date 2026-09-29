# Security Model

## Authentication

- BCrypt password hashing.
- Short-lived access JWT (default 15 minutes).
- Opaque refresh token stored as SHA-256 hash, never in plaintext.
- Refresh-token rotation on use.
- Logout revokes the current refresh token.
- Stateless API security except refresh-token records.

## Authorization

Every private domain query includes the authenticated user identifier. Controllers never accept an arbitrary `ownerId` from the client.

### RAG tenant isolation

The primary invariant:

> A private vector query must include `ownerId == authenticatedUserId`.

Document chat further adds `documentId == requestedDocumentId`. The service also checks relational ownership before retrieval.

## Upload security

MVP controls:

- allow-list extensions: PDF, EPUB, TXT, MD/Markdown;
- max size configured by Spring multipart settings and app property;
- generated storage key instead of trusting original filename;
- normalized path check to prevent path traversal;
- Tika parse failure becomes FAILED status;
- files served only through authenticated endpoints (no public directory mapping).

Production hardening should add malware scanning before parsing if anonymous/public uploads are introduced.

## Prompt injection

- Treat retrieved document text as untrusted data.
- Delimit retrieved context clearly.
- System prompt says document instructions are not executable instructions.
- Tool set is explicitly scoped per assistant request.
- Tool methods re-check authorization instead of trusting LLM arguments.
- Do not place secrets in prompts, vector metadata or tool descriptions.

## Rate limiting

MVP enforces an in-memory per-user fixed-window limit of 30 AI operations/minute in `AiRateLimiter` and also respects upstream/provider quotas. A multi-instance deployment should move the counter to Redis or another shared store.

## Secrets

No API keys are committed. Local values come from `.env`; production should use the deployment platform's secret manager.
