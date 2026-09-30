# Security Model

## Authentication

- BCrypt password hashing.
- Short-lived access JWT (default 15 minutes).
- Opaque refresh token stored as SHA-256 hash, never in plaintext.
- Refresh-token rotation on use, with **reuse detection**: presenting an already-revoked refresh token revokes every
  session of that user (the token was copied and used twice).
- Logout revokes the current refresh token.
- Stateless API security except refresh-token records.
- `JWT_SECRET` must be at least 32 characters; in the `prod` profile `ProdSecretGuard` refuses to start with the
  default secret shipped in the repository.
- Account deletion (`DELETE /api/auth/me`, password required) removes the user's library, progress, documents,
  vectors, uploaded files and refresh tokens.

## Authorization

Every private domain query includes the authenticated user identifier. Controllers never accept an arbitrary `ownerId` from the client.

### RAG tenant isolation

The primary invariant:

> A private vector query must include `ownerId == authenticatedUserId`.

Document chat further adds `documentId == requestedDocumentId`. The service also checks relational ownership before retrieval.

## Upload security

MVP controls:

- allow-list extensions: PDF, EPUB, TXT, MD/Markdown;
- declared Content-Type must match the extension, and the actual bytes are sniffed with Tika (a `.pdf` containing HTML is rejected);
- max size 25 MB per file (Spring multipart + app property; larger bodies get `413 FILE_TOO_LARGE`);
- per-user quotas: 50 documents and 500 MB (`APP_MAX_DOCUMENTS_PER_USER`, `APP_MAX_BYTES_PER_USER`);
- extracted text capped at 2,000,000 characters;
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

All limits are in-memory fixed windows per instance. A multi-instance deployment should move the counters to Redis
or another shared store.

| Limit | Key | Default | Error |
|---|---|---|---|
| Login and register | client IP + email | 10/min (`app.rate-limit.auth-per-minute`) | `429 RATE_LIMITED` |
| Anonymous search | client IP | 30/min | `429 RATE_LIMITED` |
| Signed-in search | user | 120/min | `429 RATE_LIMITED` |
| AI operations | user | 20/min (`AI_RATE_LIMIT_PER_MINUTE`) | `429 AI_RATE_LIMITED` |
| AI operations | whole instance | 25/min (`AI_GLOBAL_RATE_PER_MINUTE`) | `429 AI_RATE_LIMITED` |

Client IPs come from `X-Forwarded-For` only when `SERVER_FORWARD_HEADERS_STRATEGY` trusts the proxy (`framework`
in the `prod` profile). Provider 429s from Groq are also mapped to `AI_RATE_LIMITED`.

## CORS and operational endpoints

- Allowed browser origins come from `APP_CORS_ORIGINS` (comma-separated; default `http://localhost:3000`). With the
  default setup the browser talks only to the Next.js origin and `/api/*` is proxied, so CORS matters only when
  `NEXT_PUBLIC_API_URL` points the browser straight at the backend.
- `/actuator/health` and `/actuator/info` are public (the `prod` profile exposes only `health`, without details);
  every other actuator endpoint and `/api/admin/**` require the `ADMIN` role.
- OpenAPI (`/v3/api-docs`, `/swagger-ui.html`) is disabled in the `prod` profile.
- `APP_ADMIN_EMAILS` (comma-separated) is the only way to grant `ADMIN`; matching accounts are promoted at startup and on
  registration, and there is no self-service role change.
- Expired or revoked refresh tokens are purged daily after a one-day grace period.
- 401/403 responses from the security layer use the standard error envelope.

## Browser security headers

The Next.js app sends, on every route: a Content-Security-Policy (`default-src 'self'`, images only from self and
the Open Library / Google Books cover hosts, `connect-src 'self'` plus `NEXT_PUBLIC_API_URL` when set,
`frame-ancestors 'none'`, `object-src 'none'`), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
`Referrer-Policy: strict-origin-when-cross-origin` and a restrictive `Permissions-Policy`. `script-src` still
allows `'unsafe-inline'` because Next injects inline bootstrap scripts; a nonce-based CSP is a V2 item.

## AI and vector features when disabled

With `AI_ENABLED=false` no chat call is made and AI endpoints return `503 AI_DISABLED`. Embeddings are local, so
search, recommendations and document ingestion keep working. With `VECTOR_ENABLED=false` book indexing is
skipped, semantic search contributes nothing, and document ingestion fails with an explicit message.

## Known MVP trade-offs

| Trade-off | Why it is accepted | Upgrade path |
|---|---|---|
| Access and refresh tokens are kept in `localStorage`, so an XSS bug could read them | Simple SPA session with no cookie/CSRF handling; CSP, React escaping and no `dangerouslySetInnerHTML` limit XSS | Refresh token in an `httpOnly`, `SameSite=Strict` cookie set by the backend, access token in memory only |
| A deleted user's access token stays valid until it expires (up to 15 minutes); requests then fail because the user's data is gone | Access tokens are stateless; refresh tokens are deleted immediately so the session cannot be extended | Short TTL is the mitigation; a token denylist or user-version claim if needed |
| Rate limits and AI limits are per instance, in memory | Single instance in MVP | Redis-backed counters |
| CSP allows inline scripts | Required by Next.js without nonces | Nonce-based CSP via middleware |
| No malware scanning of uploads | Only the owner can read their own files; files are never served to other users | Scan before parsing if sharing is added |

## Secrets

No API keys are committed. Local values come from `.env`; production should use the deployment platform's secret manager.
