<!-- GSD:project-start source:PROJECT.md -->

## Project

Airwallex FYI is a personal update monitor for Airwallex's public global Blog and Newsroom. It checks public Airwallex update sources, detects newly published items, summarizes them, and sends concise WhatsApp alerts so the user can stay current on company, product, and technical changes.

The first implementation is a Kotlin Spring Boot service designed as a learning project and a useful private tool. It favors a reliable monitor pipeline over a complex agent system.

**Core Value:** The service reliably tells the user when Airwallex publishes a new public update, with a short useful summary and a direct source link.

### Constraints

- **Tech stack**: Kotlin Spring Boot - chosen because the user wants to learn Kotlin Spring Boot and the app is a good backend learning project.
- **Source policy**: Use only public Airwallex pages and sitemaps - avoid blocked app/API paths from robots.txt.
- **Notification channel**: WhatsApp first through Twilio - selected by the user.
- **Language/source scope**: Global English Blog + Newsroom only - keeps v1 focused and avoids noisy regional duplication.
- **No historical spam**: First production run must seed existing posts without notifying.
- **Credentials**: All API keys and phone numbers must come from environment variables, not committed files.
- **Deployment shape**: Long-running Spring Boot service with scheduler plus `--run-once`; avoid mixing that with a GitHub Actions-only design.

<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->

## Technology Stack

## Recommendation

## Core Libraries

- Spring Boot Web: admin endpoints and HTTP service runtime.
- Spring Scheduling: scheduled polling.
- Spring Data JDBC: simple explicit relational persistence without JPA complexity.
- PostgreSQL driver: production persistence.
- Flyway: database migrations.
- Jackson Kotlin module: JSON handling with Kotlin types.
- Jsoup: fallback HTML extraction.
- WebClient or RestClient: outbound HTTP to Airwallex, OpenAI, and Twilio.
- JUnit 5, Spring Boot Test, MockWebServer/WireMock: focused HTTP and service tests.

## Notes

## Confidence

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:

- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
