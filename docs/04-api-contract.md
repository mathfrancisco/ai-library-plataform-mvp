# REST API Contract

This file is a short index. The full, generated contract (request/response schemas, parameters, status codes) is
served by the backend outside the `prod` profile:

- OpenAPI JSON: `GET /v3/api-docs`
- Swagger UI: `/swagger-ui.html`

Base path: `/api`. The browser calls `/api/*` on the frontend origin; the Next proxy forwards to the backend.
Authenticated endpoints need `Authorization: Bearer <access token>`.

## Auth

| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/register` | Create account (201) and return access/refresh tokens |
| POST | `/auth/login` | Authenticate (limited per IP + email) |
| POST | `/auth/refresh` | Rotate refresh token; reusing a rotated token revokes every session of the user |
| POST | `/auth/logout` | Revoke one refresh token |
| POST | `/auth/logout-all` | Revoke every refresh token of the current user |
| GET / PATCH | `/auth/me` | Current profile (`createdAt` included) / change display name |
| POST | `/auth/me/password` | Change password; other sessions are revoked, a new pair is returned |
| DELETE | `/auth/me` | Delete the account and all its data (`{"password": "…"}`) |

## Catalog and books

| Method | Path | Purpose |
|---|---|---|
| GET | `/catalog/search?q=&page=&size=` | Federated external search; `total` is approximate, `providers[]` reports each provider |
| POST | `/catalog/import` | Persist one external record (`provider` must be `open-library` or `google-books`) |
| POST | `/books` | Manual registration: 201 when created, 200 with the existing book when it is a duplicate |
| GET | `/books/{id}` | Local book detail |
| GET | `/books/{id}/similar` | Similar local books (vector similarity) |
| POST | `/books/{id}/summary?type=TLDR\|SHORT\|TAKEAWAYS` | Cached AI summary of the catalog description |
| POST | `/books/{id}/chat` | Grounded RAG over the user's documents attached to the book |

## Search

| Method | Path | Purpose |
|---|---|---|
| GET | `/search?q=&mode=&limit=` | `{results, degraded, providers}`; modes `HYBRID`, `LEXICAL`, `SEMANTIC` |
| POST | `/search/discover` | Natural language (`{"prompt": "…"}`, ≤ 500 chars) → plan + filtered results |

Anonymous search is limited to 30 requests/min per IP, signed-in search to 120/min per user.
Each hit has `matchType` (`LEXICAL`, `SEMANTIC`, `EXTERNAL`, or `HYBRID` when several sources agree) and `matchedBy`.

## Personal library and reading

| Method | Path | Purpose |
|---|---|---|
| GET | `/library?status=` | Compact items (no description) with progress percentage |
| GET | `/library/books/{bookId}` | One item with full book and progress; 404 when not on the shelf |
| POST | `/library/books/{bookId}` | Add (idempotent: returns the existing item unchanged) |
| PATCH | `/library/books/{bookId}` | Update status/favorite/rating (`rating: 0` clears); 404 when not on the shelf |
| DELETE | `/library/books/{bookId}` | Remove the item and its reading progress |
| GET | `/reading/{bookId}` | Progress, or an empty view with `exists: false` |
| PUT | `/reading/{bookId}` | Save page/percentage/dates/notes |

Rules (all in `LibraryService`): status `READING` sets `startedAt`; status `READ` sets `completedAt`, 100% and the
last page; the first progress save moves `WANT_TO_READ` (or a book not on the shelf) to `READING`; progress at 100%
moves the book to `READ`. The percentage follows the page when the page count is known.

## Documents and RAG

| Method | Path | Purpose |
|---|---|---|
| POST | `/documents?bookId=` | Multipart upload; with `bookId` the book goes on the shelf as `READING` |
| GET | `/documents` | Current user's documents (`failureReason` + safe `errorMessage` when `FAILED`) |
| GET | `/documents/{id}` | One document |
| POST | `/documents/{id}/reingest` | Index again (after a failure or an embedding change) |
| DELETE | `/documents/{id}` | Delete vectors and row; the file is removed after commit |
| POST | `/documents/{id}/chat` | Tenant-filtered RAG answer with sources (`label, source, documentId, chunkIndex, score, snippet`) |

Limits: 25 MB per file; 50 documents and 500 MB per user; 2 M extracted characters per document.

## AI assistant, recommendations, dashboard

| Method | Path | Purpose |
|---|---|---|
| POST | `/ai/assistant` | `{message, history[≤ 20 turns]}`; tool calling over the user's shelf |
| GET | `/ai/usage` | Last 30 days, with breakdowns by model and operation |
| GET | `/recommendations?limit=` | `[{book, score, reasons[]}]`; excludes books already on the shelf |
| GET | `/dashboard` | Status counts, favorites, progress, rating, completed this year, currently reading, recent activity |

## Admin

Requires the `ADMIN` role (`APP_ADMIN_EMAILS`).

| Method | Path | Purpose |
|---|---|---|
| POST | `/admin/books/reindex` | Re-embed every local book |

## Error envelope

Every error — including 401/403 from the security layer — uses the same body. `requestId` matches the
`X-Request-Id` response header and the server logs.

```json
{
  "code": "BOOK_NOT_FOUND",
  "message": "Book not found",
  "timestamp": "2026-09-30T14:00:00Z",
  "path": "/api/books/…",
  "requestId": "5fe0b4c4-818c-49b1-be10-b26bb87b2fc4"
}
```

Codes come from `ErrorCode.java`:

| HTTP | Codes |
|---|---|
| 400 | `BAD_REQUEST`, `VALIDATION_ERROR`, `UNSUPPORTED_FILE_TYPE`, `EMPTY_FILE`, `TOO_LARGE_AFTER_EXTRACTION`, `DOCUMENT_NOT_READY`, `NO_BOOK_DOCUMENTS`, `NO_SUMMARY_SOURCE`, `PROVIDER_UNAVAILABLE`, `WRONG_PASSWORD`, `QUOTA_EXCEEDED` |
| 401 | `UNAUTHORIZED`, `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN` |
| 403 | `FORBIDDEN` |
| 404 | `NOT_FOUND`, `BOOK_NOT_FOUND`, `DOCUMENT_NOT_FOUND`, `LIBRARY_ITEM_NOT_FOUND`, `EXTERNAL_BOOK_NOT_FOUND`, `USER_NOT_FOUND` |
| 405 | `METHOD_NOT_ALLOWED` |
| 409 | `CONFLICT`, `EMAIL_TAKEN`, `BOOK_ALREADY_EXISTS`, `ALREADY_IN_LIBRARY` |
| 413 | `FILE_TOO_LARGE` |
| 429 | `RATE_LIMITED`, `AI_RATE_LIMITED` |
| 500 | `INTERNAL_ERROR` |
| 502 | `AI_PROVIDER_ERROR` |
| 503 | `AI_DISABLED`, `VECTOR_DISABLED` |
| 504 | `AI_TIMEOUT` |
