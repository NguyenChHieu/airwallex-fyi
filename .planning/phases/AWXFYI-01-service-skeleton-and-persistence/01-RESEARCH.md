---
phase: "01"
name: "Service Skeleton And Persistence"
status: complete
created: 2026-06-20
---

# Phase 1 Research: Service Skeleton And Persistence

## RESEARCH COMPLETE

## Technical Approach

Create a conventional Kotlin Spring Boot service using Gradle Kotlin DSL. Keep the first phase focused on a reliable application skeleton: typed configuration, Flyway schema, Spring Data JDBC repositories, protected admin endpoints, and tests.

## Recommended Dependencies

- Spring Boot Web for JSON admin endpoints.
- Spring Validation plus Kotlin reflection for typed configuration validation.
- Spring Data JDBC for explicit persistence without JPA complexity.
- Flyway for schema migration.
- PostgreSQL driver for runtime.
- H2 in PostgreSQL compatibility mode for slice tests unless a later implementation chooses Testcontainers.
- Spring Boot Test and MockMvc for endpoint protection tests.

## Persistence Notes

The schema should represent future pipeline state without implementing the pipeline yet:

- `posts`: source URL, source type, metadata, content hash, timestamps, and processing status.
- `summaries`: structured summary payload associated with a post.
- `notification_attempts`: channel, recipient, status, provider message id, errors, and timestamps.

Use unique constraints now so later phases can rely on database-level dedupe.

## Risks And Mitigations

- Risk: Phase 1 becomes a horizontal foundation with no behavior. Mitigation: include a working admin interaction and a real repository insert/read test.
- Risk: Tests become fragile if Postgres-only column types are used. Mitigation: store JSON payloads as text in Phase 1 unless Testcontainers is added.
- Risk: Secrets leak into source. Mitigation: use environment-backed properties and commit only safe defaults or `.env.example`.
- Risk: Admin endpoints are left unprotected. Mitigation: test missing, wrong, and valid `X-Admin-Token` flows.