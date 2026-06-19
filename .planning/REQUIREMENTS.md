# Requirements: Airwallex FYI

**Defined:** 2026-06-20
**Core Value:** The service reliably tells the user when Airwallex publishes a new public update, with a short useful summary and a direct source link.

## v1 Requirements

### Source Monitoring

- [ ] **SRC-01**: Service can discover public Airwallex global Blog and Newsroom article URLs from `https://www.airwallex.com/global/sitemap-blog.xml`.
- [ ] **SRC-02**: Service excludes pagination, non-article URLs, regional duplicates, and non-public app/API paths from v1 monitoring.
- [ ] **SRC-03**: Service records source URL, source type, sitemap lastmod, and discovered timestamp for each candidate article.

### Article Extraction

- [ ] **EXT-01**: Service can extract title, publication date, description, author when available, source type, and article body from Airwallex `__NEXT_DATA__` payloads.
- [ ] **EXT-02**: Service can fall back to HTML extraction when structured page data is missing or malformed.
- [ ] **EXT-03**: Service computes a stable content hash from extracted article content.

### State And Dedupe

- [ ] **STATE-01**: Service persists URL, source type, metadata, content hash, summary JSON, notification status, and timestamps in Postgres.
- [ ] **STATE-02**: First run seeds currently discovered articles without sending WhatsApp alerts.
- [ ] **STATE-03**: Repeated runs do not notify the same article more than once.

### Summaries And Notifications

- [ ] **AI-01**: Service summarizes each new article into structured fields: headline, summary bullets, why it matters, tags, and source type.
- [ ] **NOTIF-01**: Service sends a concise WhatsApp alert for each new article through Twilio Sandbox.
- [ ] **NOTIF-02**: Service supports dry-run mode that logs the alert instead of calling Twilio.

### Operations

- [ ] **OPS-01**: Service reads database, OpenAI, Twilio, WhatsApp recipient, scheduler, dry-run, and admin token settings from environment variables.
- [ ] **OPS-02**: Service exposes protected admin endpoints for health, recent posts, and manual run-once.
- [ ] **OPS-03**: Service can run continuously on a schedule and can also execute a single check with `--run-once`.

### Quality

- [ ] **QUAL-01**: Automated tests cover sitemap filtering, article extraction, first-run seed behavior, dedupe, dry-run notification, and new-post notification flow.

## v2 Requirements

### Notifications

- **NOTIF-03**: Send Slack updates alongside WhatsApp.
- **NOTIF-04**: Support daily or weekly digest messages.
- **NOTIF-05**: Add production WhatsApp Business sender and template support.

### Sources

- **SRC-04**: Monitor Airwallex docs, status pages, and regional pages with filtering.
- **SRC-05**: Monitor external public news sources about Airwallex.

### Intelligence

- **AI-02**: Score update importance and suppress low-value marketing posts.
- **AI-03**: Answer questions over historical update summaries.

### Product Surface

- **UI-01**: Provide a small dashboard for history, source status, and manual controls.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Private Airwallex app/API paths | Not public monitoring; robots.txt blocks several app/API paths. |
| Unofficial WhatsApp Web automation | Too brittle and risky for reliable notifications. |
| Browser automation for every page | Current public pages expose structured data; use browser automation only if needed later. |
| Multi-agent architecture | Basic monitor pipeline should work before adding agents. |
| Full dashboard UI | Admin endpoints and logs are enough for v1. |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| OPS-01 | Phase 1 | Pending |
| OPS-02 | Phase 1 | Pending |
| STATE-01 | Phase 1 | Pending |
| SRC-01 | Phase 2 | Pending |
| SRC-02 | Phase 2 | Pending |
| SRC-03 | Phase 2 | Pending |
| EXT-01 | Phase 2 | Pending |
| EXT-02 | Phase 2 | Pending |
| EXT-03 | Phase 2 | Pending |
| STATE-02 | Phase 2 | Pending |
| STATE-03 | Phase 2 | Pending |
| AI-01 | Phase 3 | Pending |
| NOTIF-01 | Phase 3 | Pending |
| NOTIF-02 | Phase 3 | Pending |
| OPS-03 | Phase 4 | Pending |
| QUAL-01 | Phase 4 | Pending |

**Coverage:**
- v1 requirements: 16 total
- Mapped to phases: 16
- Unmapped: 0

---
*Requirements defined: 2026-06-20*
*Last updated: 2026-06-20 after initialization*
