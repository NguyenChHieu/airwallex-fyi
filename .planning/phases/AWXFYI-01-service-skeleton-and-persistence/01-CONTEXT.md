---
phase: "01"
name: "Service Skeleton And Persistence"
created: 2026-06-20
status: ready-for-planning
source: "conversation decisions and project initialization"
---

# Phase 1: Service Skeleton And Persistence - Context

<domain>
## Phase Boundary

Phase 1 creates the runnable Kotlin Spring Boot walking skeleton for Airwallex FYI. It should not fetch Airwallex pages, call OpenAI, or send Twilio messages yet. The phase succeeds when a local operator can start the service, load environment-backed settings, use the database schema, and call protected admin endpoints.

</domain>

<decisions>
## Implementation Decisions

### Stack
- **D-01:** Use Kotlin Spring Boot as the application framework because the project is a learning vehicle for Kotlin Spring Boot.
- **D-02:** Use Gradle Kotlin DSL, Spring Boot Web, Spring Validation, Spring Data JDBC, Flyway, PostgreSQL runtime driver, H2 for slice tests, and Spring Boot Test.
- **D-03:** Use Spring Data JDBC rather than JPA because the domain is small and explicit tables are easier to learn and debug.

### Persistence
- **D-04:** Phase 1 must create tables for posts, summaries, and notification attempts/status, with unique constraints that can support dedupe in later phases.

### Operations
- **D-05:** All secrets and deployment-specific values must be environment-backed: database, OpenAI, Twilio, WhatsApp recipient, scheduler, dry-run, and admin token.
- **D-06:** Admin endpoints are the Phase 1 operator surface: health, recent posts, and manual run-once. The run-once path may be a no-side-effect stub in this phase.

### Scope Fence
- **D-07:** Do not implement Airwallex sitemap crawling, article extraction, OpenAI summaries, Twilio sends, or scheduling behavior in Phase 1 beyond configuration placeholders and a run-once stub.

</decisions>

<canonical_refs>
## Canonical References

- `.planning/PROJECT.md` - project purpose, constraints, and key decisions.
- `.planning/REQUIREMENTS.md` - v1 requirements and traceability.
- `.planning/ROADMAP.md` - Phase 1 goal, mapped requirements, and success criteria.
- `.planning/research/SUMMARY.md` - stack and architecture summary.

</canonical_refs>

<code_context>
## Existing Codebase

No application source exists yet. The executor will create the Spring Boot project from an empty repository that currently contains GSD planning artifacts only.

</code_context>

<specifics>
## Specific Ideas

- Use package `com.airwallexfyi`.
- Use an `airwallex-fyi` configuration namespace with typed `@ConfigurationProperties`.
- Default local mode should be safe: `DRY_RUN=true`, scheduler disabled, and no external calls.
- Protect admin routes with an `X-Admin-Token` header checked against the configured admin token.
- Use `/admin/health`, `/admin/posts/recent`, and `/admin/run-once` for Phase 1.

</specifics>

<deferred>
## Deferred Ideas

- Airwallex sitemap discovery and extraction move to Phase 2.
- OpenAI summarization and Twilio WhatsApp delivery move to Phase 3.
- Real scheduled polling and command-line `--run-once` execution move to Phase 4.
- Slack, digest mode, production WhatsApp templates, and dashboard UI remain v2.

</deferred>