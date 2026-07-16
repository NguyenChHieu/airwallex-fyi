---
phase: "AWXFYI-05-telegram-spotlight-on-demand-discovery"
status: human_needed
automated_score: "6/6"
verified_at: "2026-07-14T07:13:00Z"
---

# Phase 5 Verification

## Automated Checks

- PASS: `/spotlight` routes through the existing allowlisted Telegram command flow.
- PASS: existing current summary is reused with zero fetch and zero AI calls.
- PASS: missing summary is persisted once.
- PASS: missing body hydrates only the selected URL.
- PASS: historical `BASELINED` status is preserved and digest eligibility remains empty.
- PASS: duplicate webhook retry sends one acknowledgement/result pair.
- PASS: full Gradle suite passed with 143 tests and no failures.

## Human Verification

- Deploy the revision to Render.
- Send `/spotlight` from an allowed Telegram chat.
- Confirm the bot first acknowledges the request, then sends one Spotlight containing source type, headline, bullets, why it matters, and the Airwallex link.
- Repeat `/spotlight` and confirm it can return another recent post without subscribing or sending a daily digest.

Automated verification passed. Phase completion is waiting only on the live Telegram check.
