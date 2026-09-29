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
| GET | `/search?q=&mode=HYBRID` | Hybrid local semantic + FTS + external search |
| POST | `/search/discover` | Natural-language discovery request |

Modes: `LEXICAL`, `SEMANTIC`, `HYBRID`.

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
| GET | `/recommendations` | Rule + similarity recommendations |
| GET | `/dashboard` | Reading counts/progress summary |

## Error envelope

```json
{
  "code": "BOOK_NOT_FOUND",
  "message": "Book not found",
  "timestamp": "2026-09-29T14:00:00Z",
  "path": "/api/books/..."
}
```
