---
phase: AWXFYI-01-service-skeleton-and-persistence
plan: "03"
subsystem: admin-operations
tags: [kotlin, spring-webmvc, mockmvc, admin-token]
requires: [01-01, 01-02]
provides:
  - Admin token protection for /admin/** routes
  - Protected health, recent posts, and run-once endpoints
  - Phase 1 run-once stub with no external side effects
affects: [phase-2-source-monitoring, phase-3-notifications, phase-4-scheduling]
tech-stack:
  added: [OncePerRequestFilter, MockMvc admin endpoint tests]
  patterns: [configuration-backed route protection, no-side-effect operator stub]
key-files:
  created:
    - src/main/kotlin/com/airwallexfyi/admin/AdminTokenFilter.kt
    - src/main/kotlin/com/airwallexfyi/admin/AdminController.kt
    - src/main/kotlin/com/airwallexfyi/admin/AdminDtos.kt
    - src/main/kotlin/com/airwallexfyi/monitor/MonitorRunService.kt
    - src/test/kotlin/com/airwallexfyi/admin/AdminControllerSecurityTest.kt
    - src/test/kotlin/com/airwallexfyi/admin/AdminControllerTest.kt
  modified: []
key-decisions:
  - "Protected only /admin and /admin/** so framework and non-admin paths stay untouched."
  - "Used X-Admin-Token from AppProperties rather than a hard-coded secret."
  - "Kept run-once as an explicit stub response that reports no Airwallex, OpenAI, or Twilio calls were made."
patterns-established:
  - "Admin endpoints are tested through MockMvc with the configured token header."
  - "Operator responses use DTOs rather than exposing persistence records directly."
requirements-completed: [OPS-02]
duration: 11min
completed: 2026-06-20
---

# Phase AWXFYI-01 Plan 03: Protected Admin Operator Surface Summary

**Authenticated admin endpoints for health, recent posts, and Phase 1 run-once stub**

## Performance

- **Duration:** 11 min
- **Completed:** 2026-06-20T00:53:23Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- Added `AdminTokenFilter` for `/admin/**` using the configured `airwallex-fyi.admin.token` value.
- Added tests proving missing and wrong `X-Admin-Token` headers return 401, while the configured token is accepted.
- Added `GET /admin/health` returning service status, dry-run, and scheduler flags.
- Added `GET /admin/posts/recent` backed by `PostRepository` and capped at 20 records.
- Added `POST /admin/run-once` delegating to `MonitorRunService.runOnce()`.
- Kept `MonitorRunService` as a Phase 1 stub that makes no Airwallex, OpenAI, or Twilio calls.
- Verified admin endpoint tests and the full Gradle test suite.

## Task Commits

Each task was committed atomically:

1. **Task 1: Protect admin routes with the configured token** - `9ebb905` (feat)
2. **Task 2: Add admin health, recent posts, and run-once endpoints** - `a212896` (feat)

## Files Created/Modified

- `src/main/kotlin/com/airwallexfyi/admin/AdminTokenFilter.kt` - Route filter for `X-Admin-Token` protection on admin paths.
- `src/main/kotlin/com/airwallexfyi/admin/AdminController.kt` - Health, recent posts, and run-once endpoints.
- `src/main/kotlin/com/airwallexfyi/admin/AdminDtos.kt` - Admin API response DTOs.
- `src/main/kotlin/com/airwallexfyi/monitor/MonitorRunService.kt` - Phase 1 run-once stub service.
- `src/test/kotlin/com/airwallexfyi/admin/AdminControllerSecurityTest.kt` - Security behavior coverage for token filtering.
- `src/test/kotlin/com/airwallexfyi/admin/AdminControllerTest.kt` - Authorized admin endpoint behavior coverage.

## Decisions Made

- Used `OncePerRequestFilter` with a narrow path check for `/admin` and `/admin/**`.
- Used constant-time token comparison for the configured token and rejected blank configured tokens.
- Used a test-only admin probe route in the security test so the token-filter task can stand independently from the real endpoint task.
- Returned DTOs from admin endpoints to keep API shape separate from persistence records.

## Deviations from Plan

None - plan executed as written.

---

**Total deviations:** 0 auto-fixed.
**Impact on plan:** None.

## Issues Encountered

None.

## Manual Smoke Command

Start the service:

```powershell
.\gradlew.bat bootRun
```

From another terminal, call the protected health endpoint with the default local token:

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/admin/health' -Headers @{ 'X-Admin-Token' = 'dev-admin-token' }
```

## User Setup Required

None for local Phase 1 testing. For a non-local deployment, set `ADMIN_TOKEN` to a non-default secret before exposing the service.

## Next Phase Readiness

Phase 1 now has a runnable service, durable monitor-state persistence, and a protected operator surface. Phase 2 can wire source discovery into `MonitorRunService` and use the existing repositories/admin endpoints for visibility.

## Self-Check: PASSED

- `.\gradlew.bat test --tests "*AdminControllerSecurityTest"` passed.
- `.\gradlew.bat test --tests "*AdminControllerTest"` passed.
- `.\gradlew.bat test --tests "*AdminController*"` passed.
- `.\gradlew.bat test` passed.
- `/admin/run-once` returns a stubbed response and does not call external services.

---
*Phase: AWXFYI-01-service-skeleton-and-persistence*
*Completed: 2026-06-20*