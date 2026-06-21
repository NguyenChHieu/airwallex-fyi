# Airwallex FYI

## What This Is

Airwallex FYI is a centralized update monitor for Airwallex's public global Blog and Newsroom. It checks public Airwallex update sources, detects newly published items, creates one canonical summary per post/content version, and sends concise WhatsApp daily updates to subscribed recipients so they can stay current on company, product, and technical changes.

The first implementation is a Kotlin Spring Boot service designed as a learning project and a useful private tool. It favors a reliable centralized monitor and fanout pipeline over a complex agent system.

## Core Value

The service reliably tells subscribed recipients when Airwallex publishes a new public update, with a short useful summary and a direct source link.

## Requirements

### Validated

(None yet - ship to validate)

### Active

- [ ] Monitor Airwallex global Blog and Newsroom public article URLs.
- [ ] Detect new posts without duplicate alerts.
- [ ] Extract title, date, description, author/source type, and article body from public pages.
- [ ] Create one canonical structured summary per new post/content version.
- [ ] Store seen posts, hashes, centralized summaries, subscribers, and per-subscriber delivery status in Postgres.
- [ ] Fan out WhatsApp daily updates through Twilio to active subscribers without duplicate delivery.
- [ ] Support first-run seeding so historical posts do not spam WhatsApp.
- [ ] Provide dry-run and admin run-once flows for local testing.

### Out of Scope

- Slack notifications - useful later, but WhatsApp remains the first delivery channel.
- Airwallex private APIs or app paths - only public pages allowed.
- Unofficial WhatsApp Web automation - brittle and risky compared with Twilio.
- Regional Airwallex sites, docs, status pages, and external news - defer until the global Blog + Newsroom path is stable.
- Full dashboard UI - admin endpoints and logs are enough for v1.
- Production WhatsApp Business sender/template approval - Twilio Sandbox is enough for the first working version.

## Context

The user is learning Kotlin Spring Boot and wants a realistic backend project. This project is a good fit because it exercises scheduled jobs, HTTP clients, parsing, persistence, LLM integration, third-party messaging, environment configuration, and tests without needing a large frontend. Before Phase 4, the architecture shifted from a single-recipient alert path to centralized summaries with per-subscriber delivery fanout.

Public source checks found:

- Airwallex Blog: https://www.airwallex.com/global/blog
- Airwallex Newsroom: https://www.airwallex.com/global/newsroom
- Airwallex robots.txt allows normal public crawling while disallowing app/API/private-ish paths.
- Airwallex sitemap index points to a global sitemap, and the global sitemap points to https://www.airwallex.com/global/sitemap-blog.xml.
- The blog sitemap includes global Blog and Newsroom article URLs with lastmod timestamps.
- Blog and Newsroom pages expose Next.js `__NEXT_DATA__` payloads with Contentful-like metadata; article pages also expose structured post fields.

## Constraints

- **Tech stack**: Kotlin Spring Boot - chosen because the user wants to learn Kotlin Spring Boot and the app is a good backend learning project.
- **Source policy**: Use only public Airwallex pages and sitemaps - avoid blocked app/API paths from robots.txt.
- **Notification channel**: WhatsApp first through Twilio - selected by the user.
- **Language/source scope**: Global English Blog + Newsroom only - keeps v1 focused and avoids noisy regional duplication.
- **Subscriber model**: Posts and summaries are canonical service-owned data; subscriber/channel delivery state is separate.
- **No historical spam**: First production run must seed existing posts without notifying.
- **Credentials**: All API keys and phone numbers must come from environment variables, not committed files.
- **Deployment shape**: Long-running Spring Boot service with scheduler plus `--run-once`; avoid mixing that with a GitHub Actions-only design.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Kotlin Spring Boot | Good learning match and technically appropriate for a scheduled backend monitor. | Pending |
| Blog + Newsroom v1 | Captures the user's target public updates without docs/status noise. | Pending |
| WhatsApp first | User selected WhatsApp over Slack for v1. | Pending |
| Twilio Sandbox for v1 | Fastest reliable WhatsApp prototype path. | Pending |
| Gemini structured summaries | Keeps alert format predictable and easy to test while using the selected free AI path. | Pending |
| Spring Data JDBC over JPA | Simpler persistence model for a small service with explicit tables. | Pending |
| Public sitemap discovery first | More stable and respectful than arbitrary crawling. | Pending |
| First-run seed without alerts | Prevents historical spam when the monitor first runs. | Pending |
| Centralized subscriber fanout | Create and update summaries once, then deliver per subscriber/channel with delivery records. | Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** via GSD:
1. Requirements invalidated? Move to Out of Scope with reason.
2. Requirements validated? Move to Validated with phase reference.
3. New requirements emerged? Add to Active.
4. Decisions to log? Add to Key Decisions.
5. "What This Is" still accurate? Update if drifted.

**After each milestone**:
1. Full review of all sections.
2. Core Value check - still the right priority?
3. Audit Out of Scope - reasons still valid?
4. Update Context with current state.

---
*Last updated: 2026-06-21 before Phase 4 to add centralized subscriber fanout*
