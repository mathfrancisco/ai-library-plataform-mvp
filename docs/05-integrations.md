# External Catalog Integrations

## Open Library — primary MVP provider

Use cases:

- Search API: `https://openlibrary.org/search.json`
- Work/Edition/ISBN lookup for details when necessary.
- Covers served directly from `covers.openlibrary.org` rather than proxied through this backend.

Important operational rules:

- Use low-volume real-time discovery, not bulk harvesting.
- Send an identifying `User-Agent` with contact information.
- Cache useful normalized results locally only when users interact with them.
- Prefer one batched Search API call over many per-book lookups.

Current documented request limits (verified 2026-09-29): 1 request/sec for unidentified clients and 3 requests/sec for identified clients. Cover requests by identifiers other than CoverID/OLID have a separate documented limit of 100 requests/IP per 5 minutes.

## Google Books — optional fallback

The Volumes API supports:

- query search;
- direct volume lookup;
- title/authors/publisher/published date;
- industry identifiers such as ISBN;
- descriptions;
- page count;
- categories;
- language;
- image links and preview/info links.

An API key is recommended for quota/accounting. The implementation is disabled when `GOOGLE_BOOKS_API_KEY` is empty.

## Project Gutenberg — V2 full-text/public-domain path

Do not crawl HTML pages. Gutenberg publishes:

- OPDS feeds intended for applications;
- daily machine-readable catalog metadata (RDF/CSV);
- public-domain eBook files according to its terms and jurisdictional notices.

The MVP documents the adapter boundary but does not continuously mirror the catalog. A V2 importer can use OPDS for interactive browsing or a scheduled metadata snapshot for a self-hosted index.

## Provider abstraction

```mermaid
classDiagram
  class BookCatalogProvider {
    <<interface>>
    +providerName() String
    +search(query,page,size) CatalogPage
    +get(externalId) Optional~CatalogBook~
  }
  class OpenLibraryProvider
  class GoogleBooksProvider
  class GutenbergProviderV2
  BookCatalogProvider <|.. OpenLibraryProvider
  BookCatalogProvider <|.. GoogleBooksProvider
  BookCatalogProvider <|.. GutenbergProviderV2
```

## Deduplication

Priority:

1. normalized ISBN-13;
2. normalized ISBN-10;
3. normalized `(title + first author)` fingerprint.

Provider IDs are never treated as globally unique because the same work can exist in multiple catalogs.
