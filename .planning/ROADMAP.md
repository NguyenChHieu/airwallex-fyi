# Roadmap: Airwallex FYI

## Overview

Airwallex FYI will be built as a vertical MVP across five planning phases. Each phase should leave the service more runnable and testable, with Phase 1 establishing the skeleton, Phase 2 making monitoring real, Phase 3 adding intelligence and WhatsApp sending, Phase 03.1 converting that into centralized subscriber fanout and daily digests, and Phase 4 hardening operations.

### Phase 1: Service Skeleton And Persistence

**Goal:** Create a runnable Kotlin Spring Boot service with configuration, database schema, and basic admin surface.
**Mode:** mvp

**Requirements**: OPS-01, OPS-02, STATE-01

**Success Criteria**:

1. Spring Boot Kotlin project builds and starts locally.
2. Environment-backed configuration exists for database, AI provider, Gemini, Twilio, WhatsApp recipient, scheduler, dry-run, and admin token values.
3. Flyway creates the initial tables for posts, summaries, and notification status.
4. Protected admin endpoints exist for health, recent posts, and manual run-once, even if run-once is initially stubbed.
5. Unit or slice tests verify configuration binding, persistence schema assumptions, and admin token protection.

### Phase 2: Airwallex Source Monitoring

**Goal:** Discover, extract, and dedupe public Airwallex Blog and Newsroom articles without sending notifications.
**Mode:** mvp

**Requirements**: SRC-01, SRC-02, SRC-03, EXT-01, EXT-02, EXT-03, STATE-02, STATE-03

**Success Criteria**:

1. Service fetches `https://www.airwallex.com/global/sitemap-blog.xml` and filters Blog/Newsroom article URLs only.
2. Service extracts article metadata and body from `__NEXT_DATA__`, with Jsoup fallback for malformed structured data.
3. Service stores discovered articles and stable content hashes.
4. First-run seed mode records existing articles without notification side effects.
5. Repeated runs do not create duplicate alerts or duplicate post records.

### Phase 3: Summaries And WhatsApp Alerts

**Goal:** Turn newly discovered articles into concise WhatsApp updates.
**Mode:** mvp

**Requirements**: AI-01, NOTIF-01, NOTIF-02

**Success Criteria**:

1. Service calls the configured Gemini-backed AI summarizer only for articles that need a new summary.
2. Summary output is parsed as structured JSON with headline, bullets, why-it-matters, tags, and source type.
3. WhatsApp alert formatting is concise and includes direct source link.
4. Twilio Sandbox send path works behind a notifier interface.
5. Dry-run mode logs the exact alert payload and never calls Twilio.

### Phase 03.1: Subscriber Fanout And Daily Digests (INSERTED)

**Goal:** Convert the Phase 3 single-recipient alert path into a centralized subscriber fanout model where the service creates posts and summaries once, then sends daily updates to subscribed channels.
**Mode:** mvp

**Requirements**: SUB-01, SUB-02, SUB-03, DIGEST-01, DIGEST-02
**Depends on:** Phase 3

**Success Criteria**:

1. Service models subscribers and delivery channels separately from posts and summaries.
2. Summaries remain centralized: one canonical summary per post/content version, reused across recipients and channels.
3. Daily digest/send path fans out new summaries to subscribed WhatsApp recipients; Slack/chatbot surfaces stay interface-ready but future unless explicitly pulled into v1.
4. If no new posts exist for the day, subscribers receive one no-change status message per service-local day.
5. Delivery records are per subscriber/channel so one failed recipient does not block others and duplicate sends are prevented.

**Plans:** 4 plans

Plans:

- [x] 03.1-01 Subscriber and Channel Persistence (Wave 1)
- [x] 03.1-02 Digest Delivery Persistence and Eligibility Contract (Wave 2, blocked on 03.1-01)
- [x] 03.1-03 Digest Formatting and Subscriber Fanout Service (Wave 3, blocked on 03.1-01 and 03.1-02)
- [x] 03.1-04 Monitor and Admin Digest Integration (Wave 4, blocked on 03.1-01, 03.1-02, and 03.1-03)

Cross-cutting constraints:

- One combined daily digest/status message per active subscriber channel per service-local date.
- Posts and summaries remain canonical service-owned data; subscriber/channel delivery state is separate.
- No-change messages are subscriber-facing; approval-needed items remain admin/run-result only.

### Phase 4: Scheduling, Verification, And Deployment Readiness

**Goal:** Make the monitor safe to run unattended and easy to verify.
**Mode:** mvp

**Requirements**: OPS-03, QUAL-01

**Success Criteria**:

1. Service supports scheduled polling and `--run-once` execution.
2. Tests cover sitemap filtering, extraction, first-run seed, dedupe, dry-run, and new-post notification flow.
3. README documents local setup, required env vars, Twilio Sandbox setup, Gemini setup, and run commands.
4. `.env.example` documents expected settings without secrets.
5. Final smoke test demonstrates a dry-run check against fixture or mocked Airwallex data.

## Future Phases

- Slack notifications and weekly or richer digest mode.
- Production WhatsApp Business sender/template setup.
- Docs/status/regional source monitoring.
- Importance scoring and user-controlled filters.
- Dashboard and historical Q&A over summaries.

---
*Roadmap created: 2026-06-20*

