---
phase: AWXFYI-03-summaries-and-whatsapp-alerts
plan: "04"
subsystem: monitor-admin-integration
tags: [kotlin, spring-boot, monitor, admin, ai, whatsapp]
requires:
  - phase: AWXFYI-03-summaries-and-whatsapp-alerts
    plan: "02"
    provides: ArticleSummaryService
  - phase: AWXFYI-03-summaries-and-whatsapp-alerts
    plan: "03"
    provides: WhatsAppAlertFormatter and WhatsAppNotifier
provides:
  - New-post summary and alert orchestration in run-once
  - Approval-needed handling for seeded/missing-summary and content-changed posts
  - Extended bounded run-once result fields and admin DTOs
  - Protected manual summarize endpoint without WhatsApp sending
  - Integration tests for dry-run, live-notifier fake, failures, and approval-needed work
key-files:
  modified:
    - src/main/kotlin/com/airwallexfyi/monitor/MonitorRunService.kt
    - src/main/kotlin/com/airwallexfyi/monitor/PostStateService.kt
    - src/main/kotlin/com/airwallexfyi/monitor/MonitorRunResult.kt
    - src/main/kotlin/com/airwallexfyi/admin/AdminController.kt
    - src/main/kotlin/com/airwallexfyi/admin/AdminDtos.kt
    - src/main/kotlin/com/airwallexfyi/summaries/ArticleSummaryService.kt
    - src/test/kotlin/com/airwallexfyi/monitor/MonitorRunServiceTest.kt
    - src/test/kotlin/com/airwallexfyi/admin/AdminControllerTest.kt
    - src/test/kotlin/com/airwallexfyi/admin/AdminControllerSecurityTest.kt
    - src/test/kotlin/com/airwallexfyi/summaries/ArticleSummaryServiceTest.kt
requirements-completed: [AI-01, NOTIF-01, NOTIF-02]
completed: 2026-06-21
---

# Phase AWXFYI-03 Plan 04: Monitor Pipeline and Admin Approval Integration Summary

## Accomplishments

- Wired `MonitorRunService` so only newly discovered posts auto-call `ArticleSummaryService`, format a WhatsApp payload, and call the configured notifier.
- Added post status transitions for `SUMMARY_FAILED`, `DRY_RUN_READY`, `ALERT_SENT`, `ALERT_FAILED`, `APPROVAL_NEEDED`, and manual `SUMMARY_READY`.
- Surfaced seeded/history posts with missing summaries as `missing_summary` approval-needed work without AI calls.
- Surfaced known URL content changes as `content_changed` approval-needed work without AI or duplicate notification attempts.
- Extended `/admin/run-once` with summary/alert counts, payload previews, approval-needed samples, and `twilioCallsTriggered`.
- Added `POST /admin/posts/{id}/summarize`, protected by the existing admin token filter, to manually summarize approval-needed/missing-summary posts without sending WhatsApp.
- Updated `ArticleSummaryService` to replace an existing summary row during manual re-summary while preserving the single-summary-per-post constraint.

## Task Commits

| Commit | Description |
|--------|-------------|
| fbffedc | Integrate summaries and WhatsApp notifications into monitor/admin flow with tests. |

## Deviations from Plan

- Approval-needed de-duplication is URL-based, so a post is counted once even if it could qualify for both `content_changed` and `missing_summary`.
- Manual summary approval returns `200 OK` with `SUMMARY_FAILED` and a bounded reason when AI summarization fails, keeping the operator-facing response simple and non-exceptional.

## Verification

- `./gradlew.bat test --tests "*MonitorRunServiceTest" --tests "*AdminControllerTest" --tests "*AdminControllerSecurityTest" --tests "*ArticleSummaryServiceTest"` - passed.
- `./gradlew.bat test` - passed.
- `git diff --check` - passed, with only normal Windows line-ending notices.

## User Setup Required

For live end-to-end sends, configure `GEMINI_API_KEY`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_WHATSAPP_FROM`, `WHATSAPP_TO`, and set `DRY_RUN=false`. For default dry-run, only `GEMINI_API_KEY` is needed once real summarization is exercised.

## Self-Check: PASSED

Plan 03-04 success criteria are met: new posts can summarize and notify, dry-run never reports Twilio calls, content-changed and missing-summary work is approval-needed, manual approval is protected, and run-once returns bounded counts/samples.
