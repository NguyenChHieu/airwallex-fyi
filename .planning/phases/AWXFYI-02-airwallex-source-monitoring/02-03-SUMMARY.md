---
phase: AWXFYI-02-airwallex-source-monitoring
plan: "03"
subsystem: monitor
tags: [spring-data-jdbc, h2, dedupe, seed, monitor-run]
requires:
  - phase: AWXFYI-02-01
    provides: Source discovery candidates
  - phase: AWXFYI-02-02
    provides: Article extraction and content hashes
provides:
  - Monitor run result contract with operational counts
  - First-run seed and URL dedupe behavior
  - Known URL content update path using explicit SQL
  - Partial failure handling for per-article extraction errors
affects: [admin-visibility, scheduling, notifications]
tech-stack:
  added: []
  patterns: [explicit JDBC update for known rows, sampled run errors, no notification side effects]
key-files:
  created:
    - src/main/kotlin/com/airwallexfyi/monitor/MonitorRunResult.kt
    - src/main/kotlin/com/airwallexfyi/monitor/PostStateService.kt
    - src/test/kotlin/com/airwallexfyi/monitor/PostStateServiceTest.kt
    - src/test/kotlin/com/airwallexfyi/monitor/MonitorRunServiceTest.kt
  modified:
    - src/main/kotlin/com/airwallexfyi/monitor/MonitorRunService.kt
    - src/test/kotlin/com/airwallexfyi/admin/AdminControllerTest.kt
key-decisions:
  - "Use URL existence as the definition of new; content hash changes are updates, not new posts."
  - "Use explicit NamedParameterJdbcTemplate updates for known posts to avoid Persistable save-as-new behavior."
  - "Keep externalCallsTriggered false in Phase 2 to mean no AI or notification side effects."
patterns-established:
  - "Monitor run tests use fake sitemap and article HTTP, never live Airwallex network."
  - "Run results expose sampled URLs/errors while detailed errors stay out of response payloads."
requirements-completed: [STATE-02, STATE-03, SRC-03, EXT-03]
duration: 35 min
completed: 2026-06-20
---

# Phase AWXFYI-02 Plan 03: Monitor Run Persistence, Dedupe, and Seed Behavior Summary

**Run-once source monitor that seeds recent posts, dedupes by URL, updates changed content, and survives partial extraction failures**

## Performance

- **Duration:** 35 min
- **Started:** 2026-06-20T23:25:00Z
- **Completed:** 2026-06-20T23:27:55Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments

- Replaced the Phase 1 run-once stub with discovery, extraction, and persistence orchestration.
- Added operational run counts for discovered, seeded, new, updated, skipped, and failed work.
- Added first-run seed behavior capped by irstRunSeedLimit, defaulting to 25 from config.
- Added URL-first dedupe and explicit SQL updates for known URL content hash changes.
- Added per-article failure handling with sampled URL/reason while continuing unrelated articles.
- Added tests for seed limit, repeat-run dedupe, content updates, same-hash refresh, partial failures, and sitemap failure no-write behavior.

## Task Commits

Each task was committed atomically:

1. **Task 1: Define monitor run result shape** - 88631df (feat)
2. **Task 2: Implement post state persistence rules** - 5b411c (feat)
3. **Task 3: Orchestrate discovery, extraction, and state updates** - 1cfc24 (feat)

**Plan metadata:** pending in this summary commit.

## Files Created/Modified

- src/main/kotlin/com/airwallexfyi/monitor/MonitorRunResult.kt - Operational status, counts, samples, and errors.
- src/main/kotlin/com/airwallexfyi/monitor/PostStateService.kt - Seed/new/update/skip planning and persistence.
- src/main/kotlin/com/airwallexfyi/monitor/MonitorRunService.kt - Real run-once orchestration.
- src/test/kotlin/com/airwallexfyi/monitor/PostStateServiceTest.kt - Persistence behavior coverage.
- src/test/kotlin/com/airwallexfyi/monitor/MonitorRunServiceTest.kt - Orchestration behavior coverage.
- src/test/kotlin/com/airwallexfyi/admin/AdminControllerTest.kt - Isolates admin tests from real monitor execution after run-once became real.

## Decisions Made

- Content hash changes update existing rows quietly and increment updatedCount; they do not increment 
ewCount.
- Same-hash known URLs refresh sitemap_lastmod but count as skipped, preventing repeat fetches after sitemap lastmod changes without content changes.
- First-run seed stores only the configured recent baseline; candidates outside that initial seed are counted as skipped for the run.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Isolated admin controller tests from real monitor execution**
- **Found during:** Full suite verification after replacing the stub monitor.
- **Issue:** The existing admin test called /admin/run-once, which now executed the real monitor and populated posts from the public sitemap, causing unrelated admin assertions to fail.
- **Fix:** Added a MockitoBean monitor service in AdminControllerTest and cleared post rows before each test.
- **Files modified:** src/test/kotlin/com/airwallexfyi/admin/AdminControllerTest.kt
- **Verification:** Full ./gradlew.bat test passes.
- **Committed in:** 1cfc24

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** The fix keeps tests deterministic and prevents live network calls from controller tests. It does not change production behavior.

## Issues Encountered

- Full suite initially failed because controller tests still assumed the Phase 1 stub and shared DB rows with the real monitor run. The tests now mock the monitor dependency and isolate post state.

## Verification

- .\gradlew.bat test --tests "*PostStateServiceTest" --tests "*MonitorRunServiceTest" - passed.
- .\gradlew.bat test - passed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Wave 4 can map MonitorRunResult into richer admin DTOs and add recent-post filters/body previews without changing the monitor pipeline itself.

---
*Phase: AWXFYI-02-airwallex-source-monitoring*
*Completed: 2026-06-20*
