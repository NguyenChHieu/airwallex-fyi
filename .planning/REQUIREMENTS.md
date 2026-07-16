# Requirements: Airwallex FYI

**Defined:** 2026-06-20
**Core Value:** The service reliably tells the user when Airwallex publishes a new public update, with a short useful summary and a direct source link.

## v1 Requirements

### Source Monitoring

- [x] **SRC-01**: Service can discover public Airwallex global Blog and Newsroom article URLs from `https://www.airwallex.com/global/sitemap-blog.xml`.
- [x] **SRC-02**: Service excludes pagination, non-article URLs, regional duplicates, and non-public app/API paths from v1 monitoring.
- [x] **SRC-03**: Service records source URL, source type, sitemap lastmod, and discovered timestamp for each candidate article.

### Article Extraction

- [x] **EXT-01**: Service can extract title, publication date, description, author when available, source type, and article body from Airwallex `__NEXT_DATA__` payloads.
- [x] **EXT-02**: Service can fall back to HTML extraction when structured page data is missing or malformed.
- [x] **EXT-03**: Service computes a stable content hash from extracted article content.

### State And Dedupe

- [x] **STATE-01**: Service persists URL, source type, metadata, content hash, summary JSON, notification status, and timestamps in Postgres.
- [x] **STATE-02**: First run seeds currently discovered articles without sending WhatsApp alerts.
- [x] **STATE-03**: Repeated runs do not notify the same article more than once.

### Summaries And Notifications

- [x] **AI-01**: Service summarizes each new article into structured fields: headline, summary bullets, why it matters, tags, and source type.
- [x] **NOTIF-01**: Service sends a concise WhatsApp alert for each new article through Twilio Sandbox.
- [x] **NOTIF-02**: Service supports dry-run mode that logs the alert instead of calling Twilio.

### Subscribers And Digests

- [ ] **SUB-01**: Service stores subscribers and delivery channels separately from canonical posts and summaries.
- [ ] **SUB-02**: Service keeps summaries centralized and reuses one summary per post/content version across all subscribers.
- [ ] **SUB-03**: Service can fan out notifications to all active subscribers without duplicate delivery per post/channel/recipient.
- [ ] **DIGEST-01**: Service sends one daily subscriber digest/status message per active channel: new summarized posts when available, or a no-change message when none are eligible.
- [ ] **DIGEST-02**: Service records per-subscriber delivery status so one failed recipient does not block others.

### Operations

- [x] **OPS-01**: Service reads database, Gemini, Twilio, WhatsApp recipient, scheduler, dry-run, and admin token settings from environment variables.
- [x] **OPS-02**: Service exposes protected admin endpoints for health, recent posts, and manual run-once.
- [x] **OPS-03**: Service can run continuously on a schedule and can also execute a single check with `--run-once`.

### Quality

- [x] **QUAL-01**: Automated tests cover sitemap filtering, article extraction, first-run seed behavior, dedupe, dry-run notification, and new-post notification flow.

### On-Demand Discovery

- [ ] **SPOT-01**: An allowed Telegram user can request one random post from a bounded recent Blog/Newsroom pool and receive its structured summary with a direct source link.
- [ ] **SPOT-02**: Missing Spotlight summaries are generated and persisted centrally without making historical seed/baseline posts eligible for the scheduled new-update digest.

## v2 Requirements

### Notifications

- **NOTIF-03**: Send Slack updates alongside WhatsApp.
- **NOTIF-04**: Support weekly digest mode and richer digest preferences.
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
| OPS-01 | Phase 1 | Complete |
| OPS-02 | Phase 1 | Complete |
| STATE-01 | Phase 1 | Complete |
| SRC-01 | Phase 2 | Complete |
| SRC-02 | Phase 2 | Complete |
| SRC-03 | Phase 2 | Complete |
| EXT-01 | Phase 2 | Complete |
| EXT-02 | Phase 2 | Complete |
| EXT-03 | Phase 2 | Complete |
| STATE-02 | Phase 2 | Complete |
| STATE-03 | Phase 2 | Complete |
| AI-01 | Phase 3 | Complete |
| NOTIF-01 | Phase 3 | Complete |
| NOTIF-02 | Phase 3 | Complete |
| SUB-01 | Phase 03.1 | Pending |
| SUB-02 | Phase 03.1 | Pending |
| SUB-03 | Phase 03.1 | Pending |
| DIGEST-01 | Phase 03.1 | Pending |
| DIGEST-02 | Phase 03.1 | Pending |
| OPS-03 | Phase 4 | Complete |
| QUAL-01 | Phase 4 | Complete |
| SPOT-01 | Phase 5 | Pending |
| SPOT-02 | Phase 5 | Pending |
| NOTIF-03 | Future | Pending |
| NOTIF-04 | Future | Pending |
| NOTIF-05 | Future | Pending |
| SRC-04 | Future | Pending |
| SRC-05 | Future | Pending |
| AI-02 | Future | Pending |
| AI-03 | Future | Pending |
| UI-01 | Future | Pending |

**Coverage:**

- v1 requirements: 23 total
- v2 backlog requirements: 8 total
- Mapped to phases: 23
- Future backlog: 8
- Unmapped: 0

---
*Requirements defined: 2026-06-20*
*Last updated: 2026-07-14 for Phase 5 Telegram Spotlight*
