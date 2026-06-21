---
phase: AWXFYI-03-summaries-and-whatsapp-alerts
plan: "01"
subsystem: summaries
tags: [kotlin, spring-boot, data-jdbc, persistence]
requires:
  - phase: AWXFYI-02-airwallex-source-monitoring
    provides: extracted article/post persistence foundation
provides:
  - Strict StructuredSummary validation contract
  - Spring Data JDBC mapping for the existing summaries table
  - SummaryRepository lookup by post ID
  - Expanded coarse post processing statuses for Phase 3
  - Schema tests covering summaries and notification_attempts tables
key-files:
  created:
    - src/main/kotlin/com/airwallexfyi/summaries/StructuredSummary.kt
    - src/main/kotlin/com/airwallexfyi/summaries/SummaryRecord.kt
    - src/main/kotlin/com/airwallexfyi/summaries/SummaryRepository.kt
    - src/test/kotlin/com/airwallexfyi/summaries/SummaryRepositoryTest.kt
  modified:
    - src/main/kotlin/com/airwallexfyi/posts/ProcessingStatus.kt
    - src/test/kotlin/com/airwallexfyi/persistence/PersistenceSchemaTest.kt
requirements-completed: [AI-01]
completed: 2026-06-21
---

# Phase AWXFYI-03 Plan 01: Summary Persistence and Lifecycle Status Contract Summary

## Accomplishments

- Added `StructuredSummary.validated(...)` with required headline, 3-5 bullets, why-it-matters, nonblank tags, and typed `SourceType`.
- Added `SummaryRecord` and `SummaryRepository` mapped to the existing `summaries` table, including JSON payload/tag storage helpers.
- Expanded `ProcessingStatus` with `SUMMARY_READY`, `ALERT_SENT`, `DRY_RUN_READY`, `SUMMARY_FAILED`, `ALERT_FAILED`, and `APPROVAL_NEEDED`.
- Extended persistence tests to verify Phase 3 table columns, unique constraints, and all lifecycle status strings.

## Task Commits

| Commit | Description |
|--------|-------------|
| 0160c8f | Add summary contract, persistence mapping, status enum values, and tests. |

## Deviations from Plan

- None functionally.
- Auto-fixed one Kotlin nullable metadata helper issue in `PersistenceSchemaTest` after the first targeted test compile failed.

## Verification

- `./gradlew.bat test --tests "*SummaryRepositoryTest" --tests "*PersistenceSchemaTest" --tests "*AdminControllerTest"` - passed.
- `./gradlew.bat test` - passed.

## Self-Check: PASSED

Plan 03-01 success criteria are met: strict summary contract exists, `summaries` persistence is active and test-covered, and the expanded coarse lifecycle statuses do not break existing admin/status behavior.
