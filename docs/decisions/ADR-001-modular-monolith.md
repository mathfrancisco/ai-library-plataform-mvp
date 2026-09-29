# ADR-001: Modular monolith before microservices

**Status:** Accepted

## Context

The product has multiple domains but is maintained by one developer. Distributed services would add deployment, tracing, messaging and consistency work without a demonstrated scaling need.

## Decision

Use one Spring Boot application organized by business module. Keep provider/service interfaces at integration boundaries so a future extraction is possible if measurements justify it.

## Consequences

Positive: faster iteration, simple local setup, transactional consistency, easier tests.

Trade-off: module discipline is enforced by code review/tests rather than network boundaries.
