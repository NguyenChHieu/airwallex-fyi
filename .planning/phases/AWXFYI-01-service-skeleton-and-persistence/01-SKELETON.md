# Walking Skeleton - Airwallex FYI

**Phase:** 1
**Generated:** 2026-06-20

## Capability Proven End-to-End

A local operator can start the Kotlin Spring Boot service, persist monitor state through the database layer, and call protected admin endpoints without triggering external Airwallex, OpenAI, or Twilio side effects.

## Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Framework | Kotlin Spring Boot | Matches the user's learning goal and fits a scheduled backend monitor. |
| Build | Gradle Kotlin DSL with wrapper | Standard Spring Boot Kotlin setup and reproducible local commands. |
| Data layer | PostgreSQL plus Spring Data JDBC and Flyway | Explicit tables and repositories keep persistence simple and inspectable. |
| Test database | H2 in PostgreSQL compatibility mode for Phase 1 slice tests | Avoids requiring Docker while still exercising schema and repository assumptions. |
| Auth | Shared admin token via `X-Admin-Token` header | Enough protection for private admin endpoints in a local/private v1 service. |
| Deployment target | Local long-running service first | Production deployment is deferred until the monitor behavior is proven. |
| Directory layout | Package by capability under `src/main/kotlin/com/airwallexfyi` | Keeps config, persistence, and admin surface discoverable for a learner. |

## Stack Touched in Phase 1

- [ ] Project scaffold: Gradle Kotlin Spring Boot build, wrapper, application entrypoint, test runner.
- [ ] Routing: protected `/admin/health`, `/admin/posts/recent`, and `/admin/run-once` routes.
- [ ] Database: Flyway migration plus repository test that writes and reads a post record.
- [ ] Operator interaction: authenticated HTTP admin calls through MockMvc and local run commands.
- [ ] Local run command: `./gradlew bootRun` or `./gradlew.bat bootRun` with safe local defaults.

## Out of Scope (Deferred to Later Slices)

- Airwallex sitemap discovery and article extraction.
- OpenAI summary generation.
- Twilio WhatsApp sending.
- Production scheduler behavior and CLI `--run-once` execution.
- Slack, digest mode, and dashboard UI.

## Subsequent Slice Plan

- Phase 2: Discover and extract public Airwallex Blog and Newsroom posts, then dedupe them without notifications.
- Phase 3: Summarize new posts and send WhatsApp alerts through Twilio Sandbox.
- Phase 4: Add scheduling, final verification, setup docs, and deployment readiness.