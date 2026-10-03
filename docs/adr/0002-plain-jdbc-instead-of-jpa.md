# ADR 0002: Plain SQL with JdbcClient instead of JPA

- **Status:** Accepted
- **Date:** 2026-10-03

## Context

The core of SpecLens is retrieval SQL: pgvector cosine distance (`<=>`), Postgres
full-text search (`tsvector`, `websearch_to_tsquery`, `ts_rank`), and a merge of the two
result lists. None of that maps naturally onto JPA entities or JPQL.

## Decision

Use Spring's `JdbcClient` (and `JdbcTemplate` for batch inserts) with hand-written SQL
for all data access. Map rows to Java records. Manage the schema with Flyway.

## Why

- Retrieval queries would be native SQL under JPA anyway. Using one style everywhere is
  simpler than mixing entities with native queries.
- The SQL that matters is visible in the code and can be read, explained and tuned.
- Less memory and faster startup without Hibernate, which matters on a 512 MB
  free-tier host.
- Records are immutable and need no getters, setters or proxy rules.

## Consequences

- No automatic dirty checking or lazy loading. The domain is small (project, document,
  chunk) and mostly insert-then-read, so this costs little.
- Vectors are passed to Postgres as text (`'[0.1,0.2,...]'`) and cast with `::vector`,
  which avoids an extra pgvector-Java dependency.
- Postgres constraint violations reach the app as Spring's `DuplicateKeyException` and
  are mapped to HTTP 409.
