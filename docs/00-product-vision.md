# Product Vision

## Problem

Book discovery is fragmented between metadata catalogs, personal reading lists and generic AI chats. Traditional search requires exact terms, while generic LLM answers are not grounded in the user's own books and often cannot cite the source material.

## Solution

AI Library is a digital library layer that federates public book catalogs, stores only user-relevant records locally, and adds semantic search, RAG, recommendations, summaries and a tool-enabled assistant.

## Target users

- Developers and technical readers who want to search across a personal knowledge library.
- Students and lifelong learners who need grounded summaries and explanations.
- Readers who want discovery by intent rather than exact title/author keywords.
- Portfolio reviewers evaluating practical Java + Applied AI engineering.

## Product principles

1. **Grounded AI first.** RAG answers must be based on retrieved content when the feature is in book/document mode.
2. **Rights-aware ingestion.** Full text is accepted only from permitted public-domain sources or user-provided files they are entitled to use.
3. **Federated catalog.** Do not mirror millions of catalog records into PostgreSQL.
4. **Modular monolith.** One deployable backend, domain-oriented modules, explicit boundaries.
5. **AI provider portability.** Depend on Spring AI abstractions where practical.
6. **Multi-tenant safety.** Every private retrieval path is filtered by owner identity.
7. **Observable cost and quality.** AI requests record provider/model/latency/token metadata when available.

## Core use cases

- Search `Clean Architecture` by title/author/ISBN.
- Search `books about surviving on another planet` semantically.
- Discover `a short psychological thriller in a small town` using natural language.
- Add an external catalog item to `Want to read`.
- Upload PDF/EPUB/TXT/Markdown content.
- Ask `What does this document say about hexagonal architecture?` and receive cited chunks.
- Generate a TL;DR, short or key-takeaways summary once and reuse the cached result.
- Receive related-book recommendations based on metadata + embeddings.
- Ask the assistant `What am I currently reading?` through tool calling.
