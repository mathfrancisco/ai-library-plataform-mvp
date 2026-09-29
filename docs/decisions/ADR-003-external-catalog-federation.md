# ADR-003: Federated catalog instead of bulk import

**Status:** Accepted

## Decision

Search external catalogs on behalf of users and persist normalized book records only when they enter a user workflow (library, note, document association, cache).

## Why

- matches Open Library's intended low-volume API use;
- dramatically reduces storage/ingestion complexity;
- keeps metadata fresher;
- respects provider terms and avoids unnecessary bulk collection.
