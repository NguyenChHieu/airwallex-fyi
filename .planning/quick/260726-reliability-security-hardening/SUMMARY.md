---
status: complete
date: 2026-07-26
commit: uncommitted
---

# Reliability and Security Hardening Summary

## Delivered

- Replaced the single Telegram update high-watermark dedupe check with persisted update receipts. Duplicate retries are claimed once across service instances, while valid out-of-order updates are still processed.
- Kept the polling cursor compatible with webhook receipts and release claims when processing aborts unexpectedly.
- Added 250 ms / 500 ms bounded delays between transient Gemini retries.
- Enabled PostgreSQL RLS for the new receipt table, changed the runtime image to a non-root user, and added weekly Gradle/GitHub Actions Dependabot checks.
- Replaced deprecated Jackson text accessors and removed the unused legacy WhatsApp alert formatter, its test, and one redundant test dependency.
- Enabled GitHub Dependabot vulnerability alerts and automated security updates. No current vulnerability alerts were returned.

## Verification

- Focused Telegram, webhook, persistence, Gemini, extractor, and Twilio tests passed.
- Full Gradle test suite passed on the real checkout.
- Production boot jar built successfully.
- Git diff whitespace check passed.
- Latest ten GitHub Actions scheduled runs were successful; the latest real worker completed with failed=0, summaryFailed=0, and digestFailed=0.
- Render health exposed a free-tier cold start: the first probe returned no bytes for 60 seconds, and the immediate second probe returned HTTP 200 in 0.30 seconds.

## Remaining

- Docker image build was not run because the local Docker daemon is unavailable.
- Live Render/Supabase migration and Telegram behavior require deployment after a later commit and push.
- The historical notification_attempts table is unused but was not dropped because that is a separate destructive production migration decision.
- The user's existing README.md change remains separate and untouched.
