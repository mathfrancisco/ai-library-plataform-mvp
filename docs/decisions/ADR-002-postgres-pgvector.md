# ADR-002: PostgreSQL + pgvector as both relational and vector store

**Status:** Accepted

## Decision

Use PostgreSQL for business data and pgvector for embeddings. Spring AI's VectorStore abstraction remains the application boundary.

## Why

- one operational database for MVP;
- metadata filtering supports tenant isolation;
- HNSW index is sufficient for portfolio/MVP scale;
- avoids an additional vector database until scale or retrieval requirements demand one.
