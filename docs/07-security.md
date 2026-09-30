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

MVP enforces an in-memory per-user fixed-window limit of 30 AI operations/minute (`AI_MAX_REQUESTS_PER_MINUTE`) in `AiRateLimiter` and also respects upstream/provider quotas. A multi-instance deployment should move the counter to Redis or another shared store.

## CORS and operational endpoints

- Allowed browser origins come from `CORS_ALLOWED_ORIGINS` (comma-separated; default `http://localhost:3000`).
- `/actuator/health` and `/actuator/info` are public; every other actuator endpoint requires the `ADMIN` role.
- 401/403 responses from the security layer use the standard error envelope.

## Vector operations when AI is disabled

With `AI_ENABLED=false` no embedding call is made: book indexing is skipped, semantic search contributes nothing,
and uploaded documents are marked `FAILED` with an explicit message instead of calling the provider with a placeholder key.

## Secrets

No API keys are committed. Local values come from `.env`; production should use the deployment platform's secret manager.
