# SPEC-03 — Code readability pass

**Priority:** P1 · **Depends on:** SPEC-01 · **Type:** refactor, no behavior change

## Problem

Large parts of the code are written as single minified lines. Examples:

- Frontend: every page in `src/app/**/page.tsx`, `lib/api.ts`, `lib/auth.ts`, `components/*`. A page component is one 1,500+ character line.
- Backend: `HybridSearchService`, `DocumentService`, `UserDocument`, `SearchController`, `BookRagService`, `BookSummaryService`, `AiController`, `RagService`, `SimilarBookService`, `AssistantService`, `DocumentIngestionProcessor`.

The logic is small and mostly simple; the formatting makes it hard to read, review and diff.

## Changes

### Tooling
- Frontend: add Prettier (`prettier`, `eslint-config-prettier`), `npm run format` and `format:check`; add `format:check` to CI.
- Backend: add Spotless with `palantirJavaFormat` (or Google Java Format); run `spotless:check` in `mvn verify`.
- Run both formatters once over the whole repo in a single commit titled `style: format codebase`. No other edits in that commit.

### Structural cleanup (separate commits, after formatting)
Frontend:
- Split big pages into small components where a page holds more than one concern: `book/[id]` (detail, library controls, progress form, document chat), `documents` (upload, list, chat panel), `explore` (search form, results, discovery).
- Move inline `style={{...}}` to Tailwind classes or `globals.css` classes (Tailwind 4 is already installed but barely used).
- Create `src/lib/hooks/` for shared query hooks (`useLibrary`, `useDocuments`, `useBook`).
- Replace raw `fetch` in `documents/page.tsx` upload with the shared `api()` helper so 401 refresh and errors behave the same.

Backend:
- One statement per line; constructor fields one per line.
- Keep package-by-feature layout; do not add layers.

## Acceptance criteria

- `npm run format:check`, `npm run lint`, `./mvnw spotless:check` pass in CI.
- No file has lines over 140 characters except generated files.
- All existing tests still pass; no API or UI behavior change.

## Out of scope

New features, dependency upgrades, renaming public API paths.
