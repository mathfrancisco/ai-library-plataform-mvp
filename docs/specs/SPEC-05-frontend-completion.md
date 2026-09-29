# SPEC-05 — Frontend: page-by-page fixes and completion

**Priority:** P0 for items marked 🔴, P1 otherwise · **Depends on:** SPEC-03 (formatting), SPEC-04 (API changes named per item)

Findings come from a full read of `frontend/src`. Legend: 🔴 broken · 🟠 wrong behavior / missing scope · 🟡 improvement.

---

## 0. Build 🔴

| # | Finding | Change |
|---|---|---|
| 0.1 🔴 | `frontend/public/` does not exist; `Dockerfile` runs `COPY --from=build /app/public ./public` → `docker compose build` fails. | Add `public/` with `favicon.ico` (or `.gitkeep`). |
| 0.2 🔴 | No `package-lock.json` (SPEC-01). | Commit lock; Dockerfile uses `npm ci`. |

---

## 1. Shared layer (`lib/`, `types/`, `components/`)

### 1.1 `lib/api.ts` 🟠
- **Parallel refresh race:** each 401 calls `refreshAccess()`; the backend rotates the refresh token, so the second call uses a revoked token, fails, and `auth.clear()` logs the user out. Fix with a module-level `let refreshing: Promise<boolean> | null` shared by all callers.
- **Wrong trigger:** backend returns `403` for anonymous calls today. After SPEC-04 §1 it returns `401`. Keep refresh on `401` only.
- **Error shape:** throw `ApiError` class with `status`, `code`, `message` (from backend `ApiError`), not a plain `Error`. UI can switch on `code` (`AI_DISABLED`, `AI_RATE_LIMITED`, `FILE_TOO_LARGE`, …).
- **FormData:** `api()` already skips `Content-Type` for `FormData`; use it for all uploads (today `documents/page.tsx` and `book/[id]/page.tsx` call `fetch` directly and skip refresh and error parsing).
- **Session expiry:** when refresh fails, redirect to `/login?next=<current path>` instead of silently clearing tokens.

### 1.2 `lib/auth.ts` 🟡
- Add a tiny subscribe/notify (or `useSyncExternalStore`) so `Nav` re-renders on login/logout.
- Listen to the `storage` event to sync logout across tabs.
- Keep `localStorage` for MVP; note in `07-security.md` that httpOnly cookies are the V2 target (XSS exposure).

### 1.3 `types/api.ts` 🟡
- Split `Book` (local, `id` required) from `CatalogBook` (external, `provider` + `externalId` required). Today one type with all-optional ids forces `x.book.id &&` checks everywhere.
- Add `ProgressView`, `DashboardData`, `Recommendation`, `RagAnswer`, `ApiErrorBody`, `UserView`, `AiUsage`. Remove inline duplicates (`Rag` is defined in two pages, `D` in dashboard).

### 1.4 Query hooks 🟡
Create `lib/hooks/`: `useBook(id)`, `useLibrary(status)`, `useLibraryItem(bookId)`, `useUpsertLibraryItem()`, `useProgress(bookId)`, `useSaveProgress()`, `useDocuments()`, `useUploadDocument()`, `useMe()`. Pages call hooks; mutations invalidate the right keys (`library`, `dashboard`, `recommendations`, `book`).

### 1.5 Components 🟡
- `BookCard`: when `book.id` is missing, `href="#"` makes a dead link. Render a non-link card for external books. Replace `<img>` with `next/image` (add `images.remotePatterns` for `covers.openlibrary.org`, `books.google.com`) or keep `<img>` with `loading="lazy"` and `alt={title}`; decide once.
- New: `StatusSelect`, `FavoriteToggle`, `RatingStars`, `ProgressForm`, `RagAnswerView` (answer + sources), `FileUpload` (drag/drop, progress, errors), `ErrorState` (message + retry), `Skeleton`, `Toast` (success/error feedback), `RequireAuth`.
- `Empty`: fine; reuse everywhere.

### 1.6 Layout / navigation 🟠
- `Nav` hides all links below 900 px (`.nav nav{display:none}`) with no menu → no navigation on mobile. Add a menu button + drawer.
- `Nav` "Account" always links to `/login`. Show user name + menu (Settings, Sign out) when signed in.
- Highlight the active route (`usePathname`).
- Footer: fine.

### 1.7 Route protection 🟠
Private pages (`/library`, `/book/[id]` actions, `/documents`, `/ai`, `/dashboard`, `/settings`) render errors or a misleading empty state when signed out. Add a `(private)` route group with a client `RequireAuth` layout that redirects to `/login?next=...`. `/book/[id]` stays public for reading; its actions show "Sign in to …" when anonymous.

---

## 2. Pages

### 2.1 Landing `/` 🟡
- Works. Add a short "How it works" row (search → shelf → ask your documents) and a sign-up CTA when signed out / "Go to library" when signed in.
- Remove the claim "Rerank & dedupe · RRF" if SPEC-04 §6.1 is not done.

### 2.2 Explore `/explore` (and `/search` alias)
| # | Finding | Change |
|---|---|---|
| 🟠 | `add` mutation is shared; clicking one card shows "Adding…" on every card, and there is no success/error feedback. | Track pending id; toast on success/error; after success show "In your shelf" on that card. |
| 🟠 | External results are not clickable; users cannot see details before adding. | Clicking an external card imports it (`/api/catalog/import`) and navigates to `/book/{id}`. |
| 🟠 | "Discover with AI" needs auth; anonymous users get a silent failure. | Show "Sign in to use AI discovery"; show `AI_DISABLED` / `AI_RATE_LIMITED` messages. |
| 🟠 | `search.isError` is never rendered. | `ErrorState` with retry. |
| 🟡 | Default query `software architecture` fires an external search on every visit. | Start empty; show suggestions chips instead. |
| 🟡 | Search state is not in the URL. | Use `?q=&mode=` so results are shareable and Back works. |
| 🟡 | Mode select shows raw enum names; score shown as `0.0328`. | Labels "Smart (hybrid)", "Keyword", "Meaning"; hide raw score or show as a small relevance bar. |
| 🟡 | React `key` uses index. | Key by `localBookId ?? provider+externalId`. |
| 🟡 | Show `degraded` providers from SPEC-04 §4.2/§6.5. | Small banner "Google Books unavailable". |

### 2.3 Book detail `/book/[id]`
| # | Finding | Change |
|---|---|---|
| 🟠 | No way to set status, favorite or rating (MVP scope). Only "+ Want to read". | `StatusSelect`, `FavoriteToggle`, `RatingStars` bound to `useLibraryItem(id)`; button text reflects current state. Needs `GET /api/library/books/{bookId}` (add to backend if missing, or filter list). |
| 🟠 | Progress form starts empty; existing progress is never loaded. | Load `GET /api/reading/{id}` (returns empty view after SPEC-04 §5.2); show a progress bar; compute percent from page when `pageCount` is known. |
| 🟠 | Upload handler throws inside an `onChange` (unhandled promise), no status shown, and chat is enabled before the document is `READY` (backend returns 400). | Use `useUploadDocument`; list this book's documents with status; disable Ask until one is `READY`; poll only while `PROCESSING`/`STORED`. |
| 🟠 | Summary/chat errors are not shown. | `ErrorState` / toast with backend `code`. |
| 🟡 | `if(!b) return Loading…` also covers errors and 404. | Separate loading skeleton, 404 page, error state. |
| 🟡 | Summary type fixed at `SHORT`. | Tabs TL;DR / Short / Takeaways. |
| 🟡 | Inline styles (`fontSize:50`, etc.). | Classes (SPEC-03). |

### 2.4 Library `/library`
| # | Finding | Change |
|---|---|---|
| 🟠 | On error (e.g. signed out) the page shows "Your shelf is empty". | Render `ErrorState` / redirect (1.7). |
| 🟠 | Only "Remove"; no status change, favorite or rating. | Inline `StatusSelect`, `FavoriteToggle`, `RatingStars` per card. |
| 🟡 | Remove has no confirmation or undo. | Toast with "Undo" (re-add with previous state). |
| 🟡 | Filter only by status. | Add "Favorites" filter and sort (added, title, rating). Client-side is enough. |
| 🟡 | Show progress bar on `READING` cards. | Needs progress in the list response (backend: include `percentage` in `LibraryItemView`). |

### 2.5 Documents `/documents`
| # | Finding | Change |
|---|---|---|
| 🟠 | Upload uses raw `fetch`: no refresh, no error message, no size check. | `useUploadDocument`; client-side check of extension and 25 MB before sending. |
| 🟠 | Polls every 5 s forever. | `refetchInterval` only while any doc is `STORED`/`PROCESSING`. |
| 🟠 | `FAILED` shows only the status word. | Show `errorMessage` (safe message after SPEC-04 §8.3) and a "Retry" button (`/reingest`). |
| 🟠 | No delete. | Delete button with confirm dialog (in-page, not `window.confirm`). |
| 🟡 | Selecting a doc that is not `READY` still allows asking. | Disable until `READY`. |
| 🟡 | Documents are buttons in a row. | Table/list: name, linked book, size, status badge, chunks, date, actions. |

### 2.6 AI assistant `/ai`
| # | Finding | Change |
|---|---|---|
| 🟠 | Sends only the last message; backend has no history (SPEC-04 §10.1). | Send last 10 turns as `history`. |
| 🟡 | Conversation is lost on reload. | Keep in `sessionStorage`; "New chat" button. |
| 🟡 | No suggestions for first use. | 3 example prompt chips. |
| 🟡 | Errors appear as assistant bubbles. | Error style bubble + retry. |
| 🟡 | No auto-scroll; no `aria-live`. | Scroll to last message; `aria-live="polite"` on the list. |
| 🟡 | Book titles in answers are plain text. | Out of scope for MVP (needs structured tool output). |

### 2.7 Dashboard `/dashboard`
| # | Finding | Change |
|---|---|---|
| 🟠 | Errors not shown; signed-out users see "—". | Guard + `ErrorState`. |
| 🟡 | Stats are text only. | Status breakdown bar (CSS only, no chart lib), average progress bar, "Currently reading" list with progress, recent activity (needs SPEC-04 §5.7/§7.5), AI usage card. |
| 🟡 | Recommendations appear only if non-empty, with no reason. | Always show the section; empty state "Add and rate books to get recommendations"; show `reason` (SPEC-04 §7.4). |

### 2.8 Login `/login`
| # | Finding | Change |
|---|---|---|
| 🟠 | Always redirects to `/library`. | Respect `?next=`. |
| 🟡 | Inputs have placeholders only (no labels); error shown as plain `<p>`. | `<label>` per input; error with `role="alert"`. |
| 🟡 | No password rules shown on register (backend requires 8+). | Hint text; disable submit while pending. |
| 🟡 | Signed-in users can still open `/login`. | Redirect to `next` or `/library`. |

### 2.9 Settings `/settings` — missing 🟠
Needs SPEC-04 §2.9 endpoints.
- Profile: email (read-only), display name (edit).
- Security: change password; "Sign out of all devices" (`logout-all`).
- AI usage: requests last 30 days, by model, failures; "AI disabled" state.
- Danger zone: delete account with typed confirmation ("DELETE").
- Sign out.

### 2.10 Not found / error pages 🟡
Add `app/not-found.tsx` and `app/error.tsx` using the same shell.

---

## 3. Styling and accessibility 🟡
- `globals.css` is one minified line; Tailwind 4 is imported but unused. Pick one: keep the CSS design tokens and split into readable rules, **or** move to Tailwind utilities. Recommendation: keep tokens in `:root`, readable CSS for base + components, Tailwind for layout utilities.
- Font is Arial. Load one font with `next/font` (e.g. Inter) to avoid layout shift.
- Focus styles: inputs change border only; buttons have none. Add `:focus-visible` outlines.
- Contrast: `.bookmeta .row` `#718096` on `#0f151b` is below 4.5:1; raise.
- `html lang="en"` while the owner is Brazilian: decide the UI language once (EN for portfolio is fine) and keep all copy in it.

## 4. Metadata 🟡
- Per-page `metadata` titles (`Explore · ShelfMind`, …). Book page: `generateMetadata` is not possible in a client page; split into server `page.tsx` (fetch title) + client component.

## 5. Security headers and API origin 🟠
- Tokens live in `localStorage` (§1.2), so any XSS can read them. There is no Content Security Policy and no security headers.
- Add `headers()` in `next.config.ts` for all routes: `Content-Security-Policy` (`default-src 'self'; img-src 'self' https://covers.openlibrary.org https://books.google.com data:; connect-src 'self'` + API origin if not proxied; `frame-ancestors 'none'`), `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`. Start with `Content-Security-Policy-Report-Only` if Next inline scripts break; move to enforced once clean.
- Render AI answers and RAG snippets as plain text only (today they use `whiteSpace: pre-wrap` on text — keep it that way). If Markdown rendering is added later, use a sanitizing renderer; never `dangerouslySetInnerHTML`.
- Adopt the same-origin proxy from SPEC-08 §4: `BASE = ""`, all calls to `/api/*`. This removes the build-time `NEXT_PUBLIC_API_URL` from the browser bundle.

## Acceptance criteria
- Response headers of `/` include CSP (report-only or enforced), `X-Content-Type-Options` and `Referrer-Policy`.
- Every MVP scope page exists and works signed-in and signed-out (redirect or read-only).
- Status, favorite, rating and progress can be changed from Library and Book detail, and Dashboard reflects the change without reload.
- Upload → `READY` → ask a question works from both Documents and Book detail, with visible status and errors.
- Mobile (375 px): all pages reachable through the menu; no horizontal scroll.
- Ten parallel requests with an expired access token cause exactly one refresh call.
- `npm run lint`, `npm test`, `npm run build`, `docker compose build frontend` pass.
