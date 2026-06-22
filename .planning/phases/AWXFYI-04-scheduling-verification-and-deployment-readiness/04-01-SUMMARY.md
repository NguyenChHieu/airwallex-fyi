---
phase: "AWXFYI-04-scheduling-verification-and-deployment-readiness"
plan: "04-01"
status: completed
completed_at: "2026-06-22T11:48:42Z"
requirements_completed:
  - "OPS-03"
tasks_completed: 2
tasks_total: 2
files_modified:
  - "src/main/kotlin/com/airwallexfyi/AirwallexFyiApplication.kt"
  - "src/main/kotlin/com/airwallexfyi/runtime/ApplicationExit.kt"
  - "src/main/kotlin/com/airwallexfyi/runtime/MonitorRunMode.kt"
  - "src/main/kotlin/com/airwallexfyi/runtime/MonitorRunOnceCommand.kt"
  - "src/main/kotlin/com/airwallexfyi/runtime/MonitorScheduler.kt"
  - "src/main/kotlin/com/airwallexfyi/runtime/RunOnceApplicationRunner.kt"
  - "src/test/kotlin/com/airwallexfyi/runtime/MonitorRunModeTest.kt"
  - "src/test/kotlin/com/airwallexfyi/runtime/MonitorRunOnceCommandTest.kt"
  - "src/test/kotlin/com/airwallexfyi/runtime/MonitorSchedulerTest.kt"
  - "src/test/kotlin/com/airwallexfyi/runtime/RunOnceApplicationRunnerTest.kt"
verification:
  - ".\\gradlew.bat test --tests \"*MonitorRunModeTest\" --tests \"*MonitorRunOnceCommandTest\" --tests \"*RunOnceApplicationRunnerTest\" --tests \"*MonitorSchedulerTest\" --tests \"*AppPropertiesTest\""
---

# Plan 04-01 Summary: Runtime Run Modes

## Completed

- Added `--run-once` detection through `MonitorRunMode`.
- Added `MonitorRunOnceCommand`, backed by `MonitorRunService.runOnce()`, with exit code `0` for `completed` and `1` for `partial_failure` or `failed`.
- Added `ApplicationExit` and `RunOnceApplicationRunner` so the one-shot command can terminate the Spring app without making tests exit the JVM.
- Enabled Spring scheduling and added opt-in fixed-delay polling through `MonitorScheduler`.
- Guarded the scheduler so `--run-once` mode does not also trigger a scheduled monitor execution.

## Verification

- Passed focused runtime/config verification:
  - `.\\gradlew.bat test --tests "*MonitorRunModeTest" --tests "*MonitorRunOnceCommandTest" --tests "*RunOnceApplicationRunnerTest" --tests "*MonitorSchedulerTest" --tests "*AppPropertiesTest"`

## Notes

- Scheduler remains disabled by default through `SCHEDULER_ENABLED=false`.
- Scheduler, CLI one-shot, and admin run-once all share the existing `MonitorRunService.runOnce()` orchestration path.
