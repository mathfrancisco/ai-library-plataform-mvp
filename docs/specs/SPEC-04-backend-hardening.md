# SPEC-04 — Backend: module-by-module fixes and improvements

**Priority:** P0 for items marked 🔴, P1 otherwise · **Depends on:** SPEC-01 (SPEC-02 for AI items)

Findings come from a full read of every class under `backend/src/main/java/com/ailibrary`. Each item lists the file, the problem, the change and how to prove it. One commit per item group.

Legend: 🔴 broken feature · 🟠 wrong behavior or security gap · 🟡 improvement.

---

## 0. Cross-cutting 🔴 Vector IDs are not UUIDs — all vector features are dead

**Files:** `book/service/BookVectorIndexer.java`, `document/DocumentIngestionProcessor.java`, `db/migration/V1__core_schema.sql`

- `vector_store.id` is `UUID` (V1). Spring AI `PgVectorStore` converts `Document.id` with `UUID.fromString`.
- Books are indexed with id `"book-" + bookId`; chunks with `"doc-" + docId + "-" + i`. Both fail to parse.
- Effect:
  - Book indexing throws and is swallowed with a `warn` → semantic search, similar books and vector recommendations always return nothing.
  - Document ingestion throws → every upload ends `FAILED` → document chat and book chat never work.

**Change**
- Book: `id = bookId.toString()` (one vector row per book; re-index = delete + add).
- Chunk: `id = UUID.nameUUIDFromBytes((docId + ":" + chunkIndex).getBytes(UTF_8)).toString()` (deterministic, safe to re-ingest).
- Before `add`, delete existing rows for the same book / document (`vectorStore.delete(filter)` or JDBC by metadata) so re-index does not duplicate.

**Proof:** `RagIsolationIT` and a `BookVectorIndexerIT` that indexes a book and finds it with `similaritySearch` (SPEC-06).

---

## 1. `common/error` — consistent error contract

**File:** `common/error/GlobalExceptionHandler.java`

| Gap | Today | Change |
|---|---|---|
| 🟠 No `AuthenticationEntryPoint` | Anonymous calls to private endpoints get `403` (Spring default for stateless + no entry point). Frontend only refreshes on `401`, so expired tokens never refresh. | Register an entry point returning `401` + `ApiError(UNAUTHORIZED)`, and an `AccessDeniedHandler` returning `403` + `ApiError(FORBIDDEN)` in `SecurityConfig`. |
| 🟠 No fallback handler | Unknown exceptions return Spring's default body, not `ApiError`. | `@ExceptionHandler(Exception.class)` → `500 INTERNAL_ERROR`, generic message, log with a request id. |
| 🟠 Upload too large | `MaxUploadSizeExceededException` → default `413` body. | Map to `413 FILE_TOO_LARGE`. |
| 🟠 Method validation | `@NotBlank`/`@Min`/`@Max` on `@RequestParam` raise `HandlerMethodValidationException` (Spring 6.1+), not handled. | Map to `400 VALIDATION_ERROR`. |
| 🟡 Bad JSON / bad enum / bad UUID | `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException` → default body. | Map to `400 BAD_REQUEST` with field name. |
| 🟠 Unique constraint races | `DataIntegrityViolationException` (register, import, summary cache) → 500. | Map to `409 CONFLICT`; services also catch and re-read where a race is expected. |
| 🟡 AI errors | "AI disabled" is `BadRequestException` (400). | New `AiUnavailableException` → `503 AI_DISABLED`; timeout → `504 AI_TIMEOUT`; provider 429 → `429 AI_RATE_LIMITED`. |

Add `code` constants in one `ErrorCode` enum so frontend can switch on them.

---

## 2. `auth` + `common/security`

**Files:** `AuthService`, `AuthController`, `SecurityConfig`, `JwtService`, `JwtAuthenticationFilter`, `AuthProperties`, `AuthDtos`

| # | Finding | Change |
|---|---|---|
| 2.1 🟠 | Wrong credentials and invalid refresh token return `400`. | Return `401 INVALID_CREDENTIALS` / `401 INVALID_REFRESH_TOKEN`. |
| 2.2 🟠 | CORS origin hard-coded to `http://localhost:3000`. | `app.cors.allowed-origins` (list) from `APP_CORS_ORIGINS`. `allowCredentials` can be `false` (tokens go in headers). With the same-origin proxy (SPEC-08 §4), CORS is only needed for direct API clients. |
| 2.3 🟠 | No brute-force protection on `/login` and `/register`. | Reuse a small in-memory limiter keyed by IP + email (e.g. 10/min). Return `429`. |
| 2.4 🟠 | Refresh-token reuse is not detected. A stolen token used after rotation just fails. | If a **revoked** token is presented, revoke all tokens of that user (reuse detection). |
| 2.5 🟡 | Expired/revoked refresh tokens are never deleted. | `@Scheduled` daily delete where `expires_at < now() - 1 day` or `revoked_at < now() - 1 day`. Add `@EnableScheduling`. |
| 2.6 🟡 | `register` check-then-insert race → 500. | Catch `DataIntegrityViolationException` → `409 EMAIL_TAKEN`. |
| 2.7 🟡 | `JwtService` does not validate issuer on decode. | Add `JwtValidators.createDefaultWithIssuer("ai-library")`. |
| 2.8 🟡 | Default `JWT_SECRET` works in any environment. | In profile `prod`, fail startup if secret equals the default. |
| 2.9 🟡 | Settings page needs account endpoints. | `PATCH /api/auth/me` (displayName), `POST /api/auth/me/password` (revokes all refresh tokens), `DELETE /api/auth/me` (cascade: library, progress, documents, files, vector rows, AI logs, tokens). Add `created_at` to `UserView`. |
| 2.10 🟡 | Logout requires the refresh token in the body; if the client lost it, nothing is revoked. | Add `POST /api/auth/logout-all` (authenticated) to revoke all of the user's tokens. |

---

## 3. `book`

**Files:** `BookController`, `BookService`, `BookVectorIndexer`, `SimilarBookService`, `CreateBookRequest`, `BookRepository`

| # | Finding | Change |
|---|---|---|
| 3.1 🔴 | Vector id bug (see §0). | Fix id. |
| 3.2 🟠 | `POST /api/books` lets any user create global catalog rows, with no ISBN dedup. Duplicates are easy. | Before insert, reuse `CatalogService.findExisting` logic (ISBN13 → title+author). Move shared dedup into a `BookDeduplicator` used by both. |
| 3.3 🟡 | `CreateBookRequest` has no format checks. | ISBN13 `^\d{13}$`, ISBN10 `^\d{9}[\dX]$`, `publishedYear` 0..current+1, `pageCount` 1..20000, `coverUrl` must be `https`. `authors`/`categories` as `List<String>` instead of pipe-joined strings. |
| 3.4 🟡 | `BookVectorIndexer.index` is `@Async` but called inside the creating transaction; on rollback, a vector row may exist for a non-existent book. | Publish `BookSavedEvent` and index in `@TransactionalEventListener(AFTER_COMMIT)` (same pattern as documents). |
| 3.5 🟡 | Books imported from Open Library have no description → weak embeddings, and summary always fails with "no source description". | On import from Open Library, fetch `/works/{id}.json` for `description` (string or `{value}`) and first subjects. |
| 3.6 🟡 | `SimilarBookService` swallows all errors silently. | Log at `warn` with book id. Keep empty-list fallback. |

---

## 4. `catalog`

**Files:** `CatalogService`, `OpenLibraryProvider`, `GoogleBooksProvider`, `CatalogController`, `ProviderRequestGate`

| # | Finding | Change |
|---|---|---|
| 4.1 🟠 | `RestClient` has no connect/read timeouts. A slow provider blocks request threads forever. | Shared `RestClient` builder with `JdkClientHttpRequestFactory` connect 3 s, read 8 s. |
| 4.2 🟠 | Provider failures are swallowed with no log. | Log `warn` with provider name + status; expose per-provider status in the response (`CatalogPage.providers: [{name, ok}]`) so UI can say "Google Books unavailable". |
| 4.3 🟡 | `ImportRequest` is not `@Valid`. | Add `@Valid`; restrict `provider` to known names. |
| 4.4 🟡 | Import race on `(provider, external_id)` unique → 500. | Catch `DataIntegrityViolationException`, re-read the ref, return the existing book. |
| 4.5 🟡 | Google Books thumbnails are `http://` → mixed-content block in the browser. | Rewrite to `https://` in `GoogleBooksProvider.map`. |
| 4.6 🟡 | `total` sums provider totals (double counts); paging merges page N of each provider. | Document it as "approximate"; UI shows "about N results". No complex fix for MVP. |
| 4.7 🟡 | In-memory cache clears everything at 1000 entries. | Acceptable for MVP. Swap to Caffeine (`maximumSize 1000, expireAfterWrite 5m`) only if cheap. |

---

## 5. `library` + `reading`

**Files:** `LibraryService`, `UserLibraryItem`, `LibraryDtos`, `ReadingProgressService`, `ReadingProgress`, `ReadingDtos`

| # | Finding | Change |
|---|---|---|
| 5.1 🟠 | Rating cannot be cleared (null means "no change"). | Accept `rating: 0` as "clear", or add `clearRating: true`. |
| 5.2 🟠 | `GET /api/reading/{bookId}` returns 404 when no progress exists; the UI must treat it as an error. | Return an empty `ProgressView` (page 0, 0%) with `exists: false`. |
| 5.3 🟡 | Status and progress are independent. Scope says started/completed timestamps. | Rules: status → `READING` sets `startedAt` if null; status → `READ` sets `completedAt` and percentage 100; progress reaching 100% moves status to `READ`; first progress save moves `WANT_TO_READ` → `READING`. Keep rules in `LibraryService` only. |
| 5.4 🟡 | `percentage` and `currentPage` are independent. | If book `pageCount` is known and only `currentPage` is sent, compute percentage; reject `currentPage > pageCount`. |
| 5.5 🟡 | `list` returns items with full `BookView` including long descriptions. | Add `LibraryItemSummary` (no description) for list and tool use; keep full view for detail. |
| 5.6 🟡 | Removing a book from the library keeps its reading progress. | Delete progress in the same transaction. |
| 5.7 🟡 | No `updatedAt`. Dashboard "recent activity" needs it. | Add `updated_at` to `user_library` and `reading_progress` (migration V3). |

---

## 6. `search`

**Files:** `HybridSearchService`, `SearchController`, `SearchDtos`

| # | Finding | Change |
|---|---|---|
| 6.1 🟠 | Local and external hits for the same book are not merged: local key is `local:<id>`, external key is `isbn:<isbn>`. The same book appears twice. Scope says dedup by ISBN then title+author. | Use one fingerprint for both (ISBN13 → normalized title + first author). When an external hit matches a local book, attach `localBookId` and sum scores. Move `fingerprint`/`normalize` into one `BookFingerprint` util shared with `CatalogService` (they differ today). |
| 6.2 🟠 | `/discover` `limit` is unbounded; `DiscoveryRequest.prompt` has no validation. | `@Min(1) @Max(50)` on limit; `@NotBlank @Size(max = 500)` on prompt. |
| 6.3 🟡 | Semantic branch does `books.findById` per hit (N+1). | Collect ids, one `findAllById`. |
| 6.4 🟡 | `DiscoveryPlan.language`, `categories`, `maxPages` are ignored. | Apply as post-filters on local hits when the data exists; send `language` to Open Library (`language:` query). |
| 6.5 🟡 | All exceptions swallowed silently. | Log `warn`; include `degraded: ["semantic"]` in the response when a branch fails. |
| 6.6 🟡 | Similarity threshold `0.45` was chosen for OpenAI embeddings. | Re-tune for MiniLM (start at `0.30`) and move to config (`app.search.semantic-threshold`). |

---

## 7. `recommendation` + `dashboard`

**Files:** `RecommendationService`, `DashboardService`

| # | Finding | Change |
|---|---|---|
| 7.1 🟠 | Cold start calls `books.findAll()` and limits in memory — loads the whole table. | `findAll(PageRequest.of(0, limit, Sort.by(DESC, "createdAt")))`. |
| 7.2 🟠 | Scope says "rules from library categories/authors/status"; only vector similarity exists, and it is dead (§0). | Add a rule branch: top categories + authors from `READ`/`READING`/favorites/rating ≥ 4 → FTS query on local books; merge with vector results by RRF; exclude owned and `DROPPED`. |
| 7.3 🟡 | Threshold `.45` tuned for other embeddings. | Config + re-tune (see 6.6). |
| 7.4 🟡 | Response gives no reason. | Add `reason` ("Because you liked X", "Same category: Y") to each item. |
| 7.5 🟡 | Dashboard makes 5 count queries. | One `GROUP BY status` query. Add `recentActivity` (last 5 updated items, needs 5.7) and `favorites` count. |

---

## 8. `document`

**Files:** `DocumentService`, `DocumentIngestionProcessor`, `DocumentIngestionWorker`, `UserDocument`, `DocumentController`

| # | Finding | Change |
|---|---|---|
| 8.1 🔴 | Chunk id bug (see §0). | Fix id. |
| 8.2 🟠 | The whole ingestion (Tika + embeddings) runs inside one DB transaction. `processing()` is never visible to polling clients, and a DB connection is held during embedding. | Three short transactions: mark `PROCESSING` (commit) → parse + embed outside a transaction → mark `READY`/`FAILED` (commit). |
| 8.3 🟠 | `errorMessage` stores the raw exception message and returns it to the user (may leak paths or SQL). | Store a safe message from a small mapping (`NO_TEXT`, `UNSUPPORTED_FORMAT`, `EMBEDDING_FAILED`, `UNKNOWN`); log the full exception server-side. |
| 8.4 🟠 | Validation trusts extension + client `Content-Type`; `application/octet-stream` is always accepted. | Detect type with `Tika.detect(InputStream, name)` and compare to the allowed set. |
| 8.5 🟠 | `delete` removes the file first, then DB rows. If DB fails, the row points to a missing file. | Delete vector rows + DB row in the transaction; delete the file `AFTER_COMMIT`. |
| 8.6 🟡 | Default `@Async` executor. | `ThreadPoolTaskExecutor` bean `ingestionExecutor` (core 2, max 2, queue 50); `@Async("ingestionExecutor")`. On queue full → document stays `STORED` and a scheduled job retries `STORED` docs older than 1 min. |
| 8.7 🟡 | Documents stuck in `PROCESSING` after a crash stay there forever. | On startup, reset `PROCESSING` → `STORED` and re-queue. |
| 8.8 🟡 | No re-ingest after the embedding change (SPEC-02). | `POST /api/documents/{id}/reingest` (owner only). |
| 8.9 🟡 | `bookId` param on upload is accepted but a user can attach a document to any book. | Fine (books are global), but require the book in the user's library, or auto-add it as `READING`. Pick auto-add. |
| 8.10 🟡 | Per-user quota missing. | Max 50 documents / 500 MB per user (config). |

---

## 9. `rag`

**Files:** `RagService`, `BookRagService`, `AiPromptTemplates`

| # | Finding | Change |
|---|---|---|
| 9.1 🟡 | `RagService` and `BookRagService` duplicate the context/sources building and differ in thresholds (`0.60` vs `0.58`) and topK (6 vs 8). | One `GroundedAnswerService.answer(owner, question, filter, topK)`; controllers pass the filter. Thresholds in config. |
| 9.2 🟠 | Thresholds tuned for OpenAI embeddings. With MiniLM, `0.60` will return almost nothing. | Start at `0.25–0.35`; tune with the eval set (SPEC-06). |
| 9.3 🟡 | Prompt has no size cap; 8 chunks × 800 tokens + question can exceed small Groq model context or TPM limits. | Cap context at ~4,000 tokens (drop lowest-scoring chunks). |
| 9.4 🟡 | Sources return `chunkIndex` as string. | Return `int chunkIndex`, `double score`, `documentId`. |
| 9.5 🟡 | Answers do not check that cited `[S#]` labels exist. | Strip unknown labels; if no citation at all and sources exist, append a "Sources" line. |

---

## 10. `ai`

**Files:** `AiFacade`, `AssistantService`, `LibraryAssistantTools`, `BookSummaryService`, `AiRateLimiter`, `AiUsageService`, `AiController`

| # | Finding | Change |
|---|---|---|
| 10.1 🟠 | Assistant is single-turn: backend receives only the last message, but the UI shows a conversation. Follow-up questions lose context. | `ChatRequest` gets `history: [{role, text}]` (max 10 turns, 4,000 chars total) sent from the client; no server-side memory in MVP. |
| 10.2 🟠 | `LibraryStatus.valueOf(status)` / `UUID.fromString` in tools throw on bad model input. | Validate inside tools and return a short error string to the model. Tool params use enums where Spring AI supports it. |
| 10.3 🟡 | Tools return full `LibraryItemView` with descriptions → many tokens per call. | Return compact records (id, title, authors, status, progress%). |
| 10.4 🟡 | Assistant cannot find books by title; `addLocalBookToLibrary` needs a UUID the user never sees. | Add read-only tool `findLocalBooks(query)` (FTS, top 5). |
| 10.5 🟠 | `AiFacade` logs `properties.model()`, not the model actually used. | Log per-call model (SPEC-02 routing). |
| 10.6 🟡 | Summary cache race on unique `prompt_hash` → 500. | Catch `DataIntegrityViolationException`, re-read. |
| 10.7 🟡 | `FULL` summary asks for a "detailed structured summary" of a short catalog description. | Drop `FULL`, keep `TLDR`, `SHORT`, `TAKEAWAYS`. |
| 10.8 🟡 | Rate limit is per user only; Groq limits are per key (global). | Add a global token bucket (config `app.ai.global-rate-per-minute`, e.g. 25 for free tier). |
| 10.9 🟡 | `AiUsageService` counts all time. | Last 30 days + breakdown by model and operation. |
| 10.10 🟡 | Prompts are inline strings in services. | Move system prompts to `AiPromptTemplates` (or `resources/prompts/*.st`) so they are reviewed in one place. |

---

## 11. Configuration

| # | Finding | Change |
|---|---|---|
| 11.1 🔴 | YAML parse error (SPEC-01). | Quote value. |
| 11.2 🟡 | No `prod` profile. | `application-prod.yml`: actuator `health` only, no stack traces, Flyway `validate-on-migrate`, log JSON. |
| 11.3 🟡 | Magic numbers in code (topK, thresholds, chunk size, limits). | Group under `app.rag.*`, `app.search.*`, `app.ai.*` records. |

---

## 12. Cross-cutting findings added in the second review

These were not in the first pass. Each was checked against the code.

### 12.1 🔴 Filtered vector search can return nothing (HNSW + tenant filter)

**Files:** `RagService`, `BookRagService`, `V1__core_schema.sql`

- pgvector applies the `WHERE` filter **after** the HNSW index scan. The scan returns about `hnsw.ef_search` (default 40) candidates, then the owner/document filter removes the rest.
- With a few users, it works. When one user's chunks are a small part of `vector_store`, most of the 40 candidates belong to other users or to books, and the filter leaves 0–2 rows. RAG answers "not enough context" for a document that is `READY`.
- This does not show in a one-user demo. It shows after a second user uploads a large file.

**Change**
- pgvector ≥ 0.8 (the `pgvector/pgvector:pg17` image ships it): set `hnsw.iterative_scan = relaxed_order` for filtered queries. Simplest: `ALTER DATABASE ... SET hnsw.iterative_scan = 'relaxed_order'` in a migration, or `SET LOCAL` in a JDBC call before the search.
- Document chat has a small corpus (one document). As a second guard, when the filter includes `documentId`, allow an exact scan (no index) — cost is fine up to a few thousand chunks.
- Check the installed version in `SchemaIntegrationTest` (`SELECT extversion FROM pg_extension WHERE extname = 'vector'` ≥ `0.8.0`).

**Proof:** `RagIsolationIT` variant with 2,000 chunks of user B and 5 chunks of user A: user A's query returns user A's chunks (SPEC-06).

### 12.2 🟠 `vector_store.metadata` is `JSON`, filter indexes are never used

- Spring AI `PgVectorStore` converts filter expressions to `metadata::jsonb @@ '<jsonpath>'`. The expression indexes on `metadata->>'documentId'` / `->>'bookId'` in V1 do not match this form, so every filtered search scans the table.
- `03-domain-model.md` says "JSON metadata GIN index", which does not exist.

**Change:** in the SPEC-02 migration (`V2__embedding_384.sql`, the table is truncated anyway): `ALTER COLUMN metadata TYPE jsonb`, drop the two expression indexes, `CREATE INDEX ... USING GIN (metadata jsonb_path_ops)`. Verify with `EXPLAIN` on the exact SQL Spring AI emits (log it at `DEBUG`).

### 12.3 🟠 AI calls run inside DB transactions; failure logs are rolled back

**Files:** `BookSummaryService` (`@Transactional`), `AiFacade.saveLog`

- `summarize` is `@Transactional` and calls the LLM inside it. A slow provider holds a DB connection for the whole call (Hikari default pool = 10).
- When the LLM call throws, `AiFacade` saves a failure log and rethrows; the outer transaction rolls back and the failure log is lost. `ai_request_log` then under-reports failures — the opposite of principle 7 in `00-product-vision.md`.

**Change**
- Remove `@Transactional` from `summarize`. Read the book in its own read-only call, call the LLM with no transaction, save the cache row in a short transaction.
- `saveLog` writes with `Propagation.REQUIRES_NEW` (move it to a small `AiRequestLogWriter` bean so the proxy applies).
- Rule for the codebase: **no network call (LLM, embeddings, catalog) inside `@Transactional`.** Add it to `02-architecture.md`.

### 12.4 🟠 Catalog search caches provider failures for 5 minutes

**File:** `CatalogService.search`

- When a provider throws, the loop continues and the partial (often empty) page is cached for 5 minutes. One Open Library timeout makes that query return nothing for 5 minutes for every user.

**Change:** cache only when all enabled providers succeeded; otherwise return the partial result with `providers[].ok=false` (§4.2) and do not cache it (or cache for 30 s).

### 12.5 🟠 Public endpoints can be used to burn CPU and upstream quota

**Files:** `SecurityConfig`, `SearchController`, `CatalogController`

- `GET /api/search` and `GET /api/catalog/search` are anonymous. Each uncached call runs an embedding (local ONNX = CPU after SPEC-02) and one or two external API calls. `ProviderRequestGate` then serializes all users behind a 350 ms gap, so a burst from one client slows search for everyone.
- There is no limit per IP.

**Change:** reuse the limiter from §2.3 as a generic `RequestRateLimiter` keyed by user id or client IP: anonymous search 30/min/IP, authenticated 120/min/user. Return `429 RATE_LIMITED`. Document that behind a proxy the IP comes from `X-Forwarded-For` only when `server.forward-headers-strategy=framework` is set.

### 12.6 🟠 Free-text inputs have no size cap (prompt cost)

| Field | Today | Change |
|---|---|---|
| `AiController.ChatRequest.message` | `@NotBlank` | `@Size(max = 2000)` |
| `RagController.AskRequest.question`, `BookRagController.AskRequest.question` | `@NotBlank` | `@Size(max = 1000)` |
| `SearchController q` | `@NotBlank` | `@Size(max = 200)` |
| `ReadingDtos.UpdateRequest.notes` | none | `@Size(max = 5000)` |
| `CreateBookRequest.description`, `authorNames`, `categoryNames`, `coverUrl`, `language`, `publisher` | none | `@Size` matching column or a sane cap (description 10,000) |

### 12.7 🟠 `CurrentUser` throws `BadRequestException` (400) when no user

- If a private endpoint is ever added to `permitAll` by mistake, the client gets `400 BAD_REQUEST` instead of `401`, and the frontend does not refresh.

**Change:** throw a new `UnauthorizedException` mapped to `401 UNAUTHORIZED` in `GlobalExceptionHandler`.

### 12.8 🟡 Hybrid search branches run one after another

**File:** `HybridSearchService.search`

- `02-architecture.md` shows the three branches in a `par` block. The code runs lexical, semantic and external in sequence. Latency = sum, not max.

**Change:** run the three branches with `CompletableFuture` on a small bounded executor (or virtual threads: `spring.threads.virtual.enabled=true`, Java 21), each with a timeout (external 3 s, semantic 2 s). A timed-out branch adds to `degraded` (§6.5). If not done, fix the diagram instead.

### 12.9 🟡 Smaller correctness items

| # | File | Finding | Change |
|---|---|---|---|
| a | `BookRepository.findFingerprint` | Author goes into `LIKE '%' || :author || '%'` unescaped; `%` or `_` in a name widens the match. Title match is exact lowercase, so `"Clean Code"` vs `"Clean code: a handbook"` never dedups. | Compare the normalized fingerprint from `BookFingerprint` (§6.1). Store it in a new `books.fingerprint` column with an index (migration V3), filled on insert. |
| b | `RecommendationService` | Injects `VectorStore` directly while every other service uses `ObjectProvider<VectorStore>`. | Use `ObjectProvider` for consistency; cold-start path must work without a vector store. |
| c | `LibraryController` | `POST` and `PATCH /library/books/{id}` do the same upsert. `PATCH` on a book not in the library silently adds it. | `POST` = add (409 if exists, or idempotent return), `PATCH` = update (404 if missing). Update `04-api-contract.md`. |
| d | `DashboardService` | Average progress counts progress rows of books no longer in the library. | Fixed by §5.6; also compute from a join on `user_library`. |
| e | `DocumentService.upload` | File is written to disk before the DB insert; if the insert fails, the file stays orphaned. | On exception, delete the file; or register a `TransactionSynchronization.afterCompletion` that deletes it on rollback. |
| f | `DocumentIngestionProcessor` | Tika output is not capped. A small EPUB (zip) can expand to hundreds of MB of text; `withMaxNumChunks(5000)` limits chunks only after full extraction. | Cap extracted characters (e.g. 2 M chars via `BodyContentHandler(limit)` or truncate before splitting) and fail with `TOO_LARGE_AFTER_EXTRACTION`. |
| g | `AssistantService` | No cap on tool-call iterations. | Use Spring AI's max iterations option if available in 2.0.1 (verify with Context7), otherwise a counter inside the tools object that returns an error after 5 calls. |
| h | `JwtAuthenticationFilter` | A token for a deleted user stays valid until expiry (15 min). | Accept for MVP. Note it in `07-security.md`. |

---

## Acceptance criteria
- Every 🔴 item fixed and covered by a test in SPEC-06.
- No `@Transactional` method calls an LLM, embedding model or catalog provider (grep check in review).
- Every error response body is an `ApiError` with a stable `code`.
- `docs/04-api-contract.md` updated with new endpoints, fields and error codes.
