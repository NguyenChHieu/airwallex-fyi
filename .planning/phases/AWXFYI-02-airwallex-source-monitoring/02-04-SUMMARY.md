---
phase: AWXFYI-02-airwallex-source-monitoring
plan: "04"
subsystem: admin
status: complete
tags: [admin-api, monitor-visibility, filtering, spring-mvc]
requires:
  - phase: AWXFYI-02-03
    provides: Monitor run result counts, samples, and persisted post state
provides:
  - Admin run-once response with monitor counts and sampled URLs/errors
  - Recent post source/status filters
  - Body previews without full article bodies
  - Admin controller coverage for visibility, filters, partial failures, and auth preservation
affects: [operations, manual-run, source-monitoring]
tech-stack:
  added: []
  patterns: [small admin query service, enum query validation, preview-only response payloads]
key-files:
  created:
    - src/main/kotlin/com/airwallexfyi/admin/AdminPostQueryService.kt
    - src/main/kotlin/com/airwallexfyi/posts/ProcessingStatus.kt
  modified:
    - src/main/kotlin/com/airwallexfyi/admin/AdminController.kt
    - src/main/kotlin/com/airwallexfyi/admin/AdminDtos.kt
    - src/test/kotlin/com/airwallexfyi/admin/AdminControllerTest.kt
key-decisions:
  - "Map AdminRunOnceResponse directly from MonitorRunResult so admin output reflects seeded, new, updated, skipped, and failed counts."
  - "Use Spring enum conversion for sourceType and processingStatus so invalid filters return 400."
  - "Return a 240-character normalized bodyPreview and omit full articleBody from recent-post responses."
patterns-established:
  - "Admin controller tests mock MonitorRunService to avoid live discovery from controller tests."
  - "Admin recent-post queries stay behind the existing token filter."
requirements-completed: [STATE-02, STATE-03]
duration: 25 min
completed: 2026-06-21
---

# Phase AWXFYI-02 Plan 04: Admin Run Visibility and Recent Post Filters Summary

**Admin API visibility for monitor runs and safe recent-post inspection**

## Performance

- **Duration:** 25 min
- **Started:** 2026-06-21T09:24:00+10:00
- **Completed:** 2026-06-21T09:36:00+10:00
- **Tasks:** 3
- **Files modified:** 5
- **Files created:** 2

## Accomplishments

- Expanded `/admin/run-once` to return status, message, sitemapFetched, discovered, seeded, new, updated, skipped, failed, sampled URLs, sampled errors, and `externalCallsTriggered`.
- Added `/admin/posts/recent` filters for `sourceType` and `processingStatus` using typed enum query parameters.
- Added `AdminPostQueryService` to keep recent-post filtering, ordering, limit handling, and DTO mapping out of the controller.
- Added `ProcessingStatus` enum for typed admin filter validation.
- Added `bodyPreview` capped at 240 characters and kept full `articleBody` out of the list response.
- Preserved admin token protection for health, run-once, and recent-post endpoints.
- Added controller tests for completed run output, partial failure samples, body preview truncation, source/status filters, invalid enum rejection, and unchanged health flags.

## Task Commits

1. **Tasks 1-3: Admin monitor visibility, filters, previews, and tests** - b52f44d (feat)

**Plan metadata:** pending in this summary commit.

## Files Created/Modified

- `src/main/kotlin/com/airwallexfyi/admin/AdminPostQueryService.kt` - Recent-post filtering, ordering, limit capping, and body preview mapping.
- `src/main/kotlin/com/airwallexfyi/posts/ProcessingStatus.kt` - Typed admin processing-status filter values.
- `src/main/kotlin/com/airwallexfyi/admin/AdminController.kt` - Delegates recent posts to the query service and maps run-once through DTO helper.
- `src/main/kotlin/com/airwallexfyi/admin/AdminDtos.kt` - Adds monitor count/sample fields and `bodyPreview`.
- `src/test/kotlin/com/airwallexfyi/admin/AdminControllerTest.kt` - Covers run visibility, filters, previews, invalid enums, and mocked monitor output.

## Decisions Made

- Keep `/admin/posts/recent` filtering simple and in memory over the small recent admin set for this phase instead of adding repository query variants.
- Let Spring MVC reject invalid enum query parameters as `400 Bad Request`, preventing accidental broad queries.
- Keep detailed extraction failures out of the admin response; only sampled URL/reason values are returned through `MonitorRunResult`.

## Deviations from Plan

### Intentional Scope Adjustment

**1. `PostRepository.kt` did not need changes**
- **Plan expectation:** The plan listed `PostRepository.kt` as a possible modified file for recent filtering.
- **Actual implementation:** Existing `findAll()` support was enough for the small admin query service.
- **Impact:** No behavior loss. The query service keeps the controller readable without widening repository API surface.

---

**Total deviations:** 1 intentional scope adjustment
**Impact on plan:** None; all acceptance criteria are covered by tests.

## Issues Encountered

- The first GSD key-link verification attempt used an incorrect plan filename. Re-running it against `02-04-PLAN.md` passed all 3 links.

## Verification

- `node "$env:USERPROFILE\.codex\gsd-core\bin\gsd-tools.cjs" query verify.key-links "C:\Users\nguye\Downloads\airwallex-fyi\.planning\phases\AWXFYI-02-airwallex-source-monitoring\02-04-PLAN.md"` - passed, 3/3 links verified.
- `.\gradlew.bat test --tests "*AdminControllerTest" --tests "*AdminControllerSecurityTest"` - passed.
- `.\gradlew.bat test` - passed.

## User Setup Required

None.

## Next Phase Readiness

Phase 2 admin visibility is ready for phase-level verification. Phase 3 can build AI summaries and notification delivery on top of the persisted source-monitoring state.

---
*Phase: AWXFYI-02-airwallex-source-monitoring*
*Completed: 2026-06-21*
