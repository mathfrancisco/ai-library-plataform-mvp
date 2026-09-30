# REST API Contract

Base path: `/api`

## Auth

| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/register` | Create account and return access/refresh tokens |
| POST | `/auth/login` | Authenticate |
| POST | `/auth/refresh` | Rotate refresh token and issue new access token |
| POST | `/auth/logout` | Revoke refresh token |
| GET | `/auth/me` | Current user profile |

## Catalog / books

| Method | Path | Purpose |
|---|---|---|
| GET | `/catalog/search?q=&page=&size=` | Federated external search |
| POST | `/catalog/import` | Normalize + persist one external catalog result |
| POST | `/books` | Manual book registration |
| GET | `/books/{id}` | Local persisted book detail |
| GET | `/books/{id}/similar` | Similar locally indexed books |
| POST | `/books/{id}/summary?type=SHORT` | Generate/read cached AI summary |
| POST | `/books/{id}/chat` | Grounded RAG across current user's permitted documents linked to the book |

## Search

| Method | Path | Purpose |
|---|---|---|
| GET | `/search?q=&mode=HYBRID&limit=20` | Hybrid local semantic + FTS + external search |
| POST | `/search/discover` | Natural-language discovery request (`{"prompt": "..."}`) |

Modes: `LEXICAL` (local FTS + external), `SEMANTIC` (local vectors only), `HYBRID` (all three).

Each `SearchHit` carries `matchType` (`LEXICAL`, `SEMANTIC`, `EXTERNAL`, or `HYBRID` when several sources agreed) and
`matchedBy` (the contributing sources). Local and external copies of the same book are merged by ISBN-13 → ISBN-10 →
normalized title + first author, keeping the local `localBookId`.

## Personal library

| Method | Path | Purpose |
|---|---|---|
| GET | `/library?status=` | User library |
| POST | `/library/books/{bookId}` | Add/update a local book in library |
| PATCH | `/library/books/{bookId}` | Status/favorite/rating update |
| DELETE | `/library/books/{bookId}` | Remove from library |

## Reading progress

| Method | Path | Purpose |
|---|---|---|
| GET | `/reading/{bookId}` | Get progress |
| PUT | `/reading/{bookId}` | Upsert page/percentage/dates/notes |

## Documents and RAG

| Method | Path | Purpose |
|---|---|---|
| POST | `/documents` | Multipart upload + ingest |
| GET | `/documents` | Current user's documents |
| GET | `/documents/{id}` | Document status |
| DELETE | `/documents/{id}` | Delete private file + vector rows |
| POST | `/documents/{id}/chat` | Tenant-filtered RAG chat |

## AI assistant

| Method | Path | Purpose |
|---|---|---|
| POST | `/ai/assistant` | ChatClient + tool calling |
| GET | `/ai/usage` | User AI request summary |

## Recommendations / dashboard

| Method | Path | Purpose |
|---|---|---|
| GET | `/recommendations?limit=12` | Rule + similarity recommendations: `[{book, score, reasons[]}]` |
| GET | `/dashboard` | Status counts, favorites, pages, progress on current reads, average rating, completed this year, currently reading |

Recommendations exclude every book already on the user's shelf (and other editions of it). Reasons are human-readable,
for example `More from Frank Herbert` or `Matches your interest in Science fiction`.

## Library ↔ reading progress

- Moving a book to `READING` sets `startedAt` (if empty).
- Moving a book to `READ` sets `completedAt` (if empty), `percentage = 100` and `currentPage = pageCount` when known.
- `PUT /reading/{bookId}` derives `percentage` from `currentPage` when the book has a page count, rejects pages beyond
  the page count and `completedAt` before `startedAt`.

## Error envelope

Every error — including 401/403 from the security layer — uses the same shape:

```json
{
  "code": "BOOK_NOT_FOUND",
  "message": "Book not found",
  "timestamp": "2026-09-29T14:00:00Z",
  "path": "/api/books/..."
}
```

| HTTP | Codes |
|---|---|
| 400 | `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `AI_DISABLED`, `DOCUMENT_NOT_READY`, `NO_BOOK_DOCUMENTS`, `NO_SUMMARY_SOURCE`, `UNSUPPORTED_FILE_TYPE`, `EMPTY_FILE`, `FILE_TOO_LARGE`, `PROVIDER_UNAVAILABLE` |
| 401 | `UNAUTHENTICATED`, `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN` |
| 403 | `FORBIDDEN` |
| 404 | `BOOK_NOT_FOUND`, `DOCUMENT_NOT_FOUND`, `LIBRARY_ITEM_NOT_FOUND`, `READING_PROGRESS_NOT_FOUND`, `EXTERNAL_BOOK_NOT_FOUND`, `USER_NOT_FOUND`, `NOT_FOUND` |
| 409 | `EMAIL_ALREADY_REGISTERED`, `BOOK_ALREADY_EXISTS` |
| 413 | `FILE_TOO_LARGE` (multipart limit) |
| 429 | `RATE_LIMITED` |
| 500 | `INTERNAL_ERROR` |
