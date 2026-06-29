---
phase: "AWXFYI-04-scheduling-verification-and-deployment-readiness"
status: clean_after_fixes
depth: standard
files_reviewed: 20
findings:
  critical: 0
  warning: 1
  info: 1
  total: 2
reviewed_at: "2026-06-29T00:00:00Z"
review_mode: "inline"
---

# Phase 4 Code Review

## Original Scope

Reviewed the non-planning files changed in Phase 4:

- `.env.example`
- `README.md`
- `src/main/kotlin/com/airwallexfyi/AirwallexFyiApplication.kt`
- `src/main/kotlin/com/airwallexfyi/runtime/ApplicationExit.kt`
- `src/main/kotlin/com/airwallexfyi/runtime/MonitorRunMode.kt`
- `src/main/kotlin/com/airwallexfyi/runtime/MonitorRunOnceCommand.kt`
- `src/main/kotlin/com/airwallexfyi/runtime/MonitorScheduler.kt`
- `src/main/kotlin/com/airwallexfyi/runtime/RunOnceApplicationRunner.kt`
- `src/test/kotlin/com/airwallexfyi/runtime/MonitorRunModeTest.kt`
- `src/test/kotlin/com/airwallexfyi/runtime/MonitorRunOnceCommandTest.kt`
- `src/test/kotlin/com/airwallexfyi/runtime/MonitorSchedulerTest.kt`
- `src/test/kotlin/com/airwallexfyi/runtime/RunOnceApplicationRunnerTest.kt`
- `src/test/kotlin/com/airwallexfyi/runtime/RunOnceSmokeTest.kt`

## Original Result

No critical, warning, or informational findings.

The review checked:

- `--run-once` uses the shared `MonitorRunService.runOnce()` path rather than the admin HTTP endpoint.
- Non-completed monitor statuses map to a non-zero command exit code.
- The scheduler remains opt-in and delegates to the shared monitor path.
- The scheduler guard prevents duplicate monitor execution when `--run-once` is active.
- The smoke test uses fixture/mocked providers and asserts dry-run notification behavior.
- README and `.env.example` keep local defaults safe and do not include real provider credentials.

## Original Verification Evidence

- `.\gradlew.bat test --tests "*MonitorRunModeTest" --tests "*MonitorRunOnceCommandTest" --tests "*RunOnceApplicationRunnerTest" --tests "*MonitorSchedulerTest" --tests "*AppPropertiesTest"`
- `.\gradlew.bat test --tests "*RunOnceSmokeTest"`
- `.\gradlew.bat test --tests "*AirwallexSourceDiscoveryServiceTest" --tests "*ArticleExtractorTest" --tests "*MonitorRunServiceTest" --tests "*DryRunWhatsAppNotifierTest" --tests "*DailyDigestServiceTest"`
- `.\gradlew.bat test`
- `./gradlew.bat test --tests "*RunOnceSmokeTest"`

## Supplemental Review

Reviewed the recent digest, Telegram webhook, Render deployment, Docker, and GitHub Actions changes.

### WR-001 GitHub Actions could call Telegram getUpdates after webhook setup

Status: fixed.

The app intentionally skips Telegram polling when `TELEGRAM_WEBHOOK_SECRET` is nonblank, because Telegram rejects `getUpdates` while a webhook is active. The daily workflow did not pass that secret, so scheduled `--run-once` jobs could report Telegram subscription sync failures after the webhook was registered.

Fix: pass `TELEGRAM_WEBHOOK_SECRET` through the workflow and document it as a GitHub Actions secret.

### INFO-001 Docker build context included unnecessary local/project metadata

Status: fixed.

The Docker build copied repo metadata, local caches, editor files, and env files into the build context. This was not a runtime bug, but it made deploy builds noisier and heavier than needed.

Fix: add `.dockerignore` for Git metadata, Gradle/build caches, editor files, and env files.

## Supplemental Verification

- `./gradlew.bat test`
