---
quick_id: "260629-wic"
slug: "add-telegram-latest-command"
description: "add telegram /latest command"
status: complete
commit: "5da36bc"
completed_at: "2026-06-29T13:31:49Z"
---

# Quick Summary: Telegram /latest Command

## Completed

- Added `LatestUpdatesService` to format recent summary-ready updates without calling Gemini or writing digest-delivery rows.
- Added `/latest` to `TelegramSubscriptionService`.
- Updated `/help` to mention `/latest`.
- Added a test proving `/latest` returns recent summaries without subscribing the chat.
- Updated monitor test construction for the new dependency.
- Documented `/latest` in `docs/SETUP.md`.

## Verification

- `./gradlew.bat test --tests "com.airwallexfyi.subscribers.TelegramSubscriptionServiceTest" --tests "com.airwallexfyi.subscribers.TelegramWebhookControllerTest" --tests "com.airwallexfyi.monitor.MonitorRunServiceTest"`
- `./gradlew.bat test`
