# Phase 4: Scheduling, Verification, And Deployment Readiness - Context

**Gathered:** 2026-06-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 4 hardens the existing Airwallex FYI backend so the central monitor can run unattended, run once from the command line, and be verified repeatably. It should reuse the current canonical pipeline in `MonitorRunService.runOnce()`: source discovery, article extraction, post persistence/dedupe, summary generation, subscriber seeding, daily digest fanout, and bounded run-result visibility.

Phase 4 does not add Slack delivery, chatbot conversations, Supabase/admin subscriber management, production WhatsApp template approval, distributed multi-instance scheduling locks, automatic delivery retries, or a platform-specific deployment stack unless the user explicitly pulls one into scope later.

</domain>

<decisions>
## Implementation Decisions

### Run Mechanics
- **D-01:** Implement command-line `--run-once` as a Spring Boot one-shot runner that calls the existing `MonitorRunService.runOnce()` path directly, logs the bounded `MonitorRunResult`, then exits.
- **D-02:** Do not implement `--run-once` by calling the `/admin/run-once` HTTP endpoint. The admin endpoint should remain available, but CLI execution should avoid admin-token, port, and self-HTTP complexity.
- **D-03:** Use result status for process signaling: `completed` exits successfully; `partial_failure` and `failed` should produce a non-zero exit so scripts and deployment smoke checks can notice problems.
- **D-04:** Keep one orchestration path. Scheduler, CLI run-once, and admin run-once should all delegate to `MonitorRunService.runOnce()` rather than creating separate monitor flows.

### Scheduler Strategy
- **D-05:** Implement unattended polling with an in-app Spring scheduler controlled by `airwallex-fyi.scheduler.enabled` / `SCHEDULER_ENABLED`, defaulting to disabled for local safety.
- **D-06:** Use the existing fixed-delay scheduler setting for MVP rather than adding cron/time-of-day scheduling. Document a daily interval as the normal deployment setting, with faster intervals allowed for local verification.
- **D-07:** Prevent duplicate local execution when `--run-once` is used. One-shot mode should not also start the scheduled loop in the same process.
- **D-08:** Assume one running scheduler instance for MVP. Existing daily digest dedupe protects subscriber delivery, but Phase 4 should document that multi-instance deployments need a future DB/distributed lock.

### Verification
- **D-09:** Keep automated verification fixture-backed or mocked. Phase 4 smoke checks should not require live Airwallex, Gemini, or Twilio network calls.
- **D-10:** Add focused coverage for the missing operational pieces: scheduler enabled/disabled behavior, CLI `--run-once` behavior, process/status signaling, and documented dry-run smoke flow.
- **D-11:** Reuse the existing test style: Spring Boot tests, H2 in PostgreSQL mode, fixture HTML/sitemap data, fake source/AI/notifier clients, and bounded assertions on counts/samples.
- **D-12:** Do not rewrite already-covered tests unless needed. Current tests already cover sitemap filtering, article extraction, first-run seed, dedupe, dry-run/no-change digest, new-post digest, delivery failure isolation, and content-change approval behavior.

### Docs And Deployment
- **D-13:** Add local-first documentation: `README.md` with setup, test, run-once, scheduled-run, admin, Gemini, Twilio Sandbox, and smoke-test commands.
- **D-14:** Add `.env.example` with expected settings and safe placeholders only. It must not contain real secrets.
- **D-15:** Keep deployment guidance generic for this phase: a long-running Spring Boot service with environment variables, scheduler disabled by default, and `--run-once` available for manual checks/smoke tests.
- **D-16:** Do not add Docker, Railway/Render/Fly.io, Supabase deployment wiring, or CI/CD automation in Phase 4 unless the user asks for that later.

### the agent's Discretion
- The user delegated the remaining Phase 4 choices to the agent and asked to see the considered options and selected choices. The planner may choose exact Kotlin class names, Spring runner type (`ApplicationRunner` vs `CommandLineRunner`), scheduler class names, log formatting, and test class names as long as the decisions above stay true.
- The planner may decide whether to adjust the default scheduler delay value or only document the recommended daily value, as long as local defaults remain safe and scheduled execution is opt-in.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project And Phase Scope
- `.planning/PROJECT.md` - Project purpose, Kotlin Spring Boot learning goal, centralized subscriber fanout decision, and long-running service plus `--run-once` deployment shape.
- `.planning/REQUIREMENTS.md` - Phase 4 requirements OPS-03 and QUAL-01.
- `.planning/ROADMAP.md` - Phase 4 goal, success criteria, and deferred future scope.
- `.planning/STATE.md` - Current workflow state and Phase 03.1 completion context.

### Prior Phase Context
- `.planning/phases/AWXFYI-02-airwallex-source-monitoring/02-CONTEXT.md` - Source discovery, extraction, dedupe, first-run seed, content-change handling, and Postgres decision.
- `.planning/phases/AWXFYI-03-summaries-and-whatsapp-alerts/03-CONTEXT.md` - Gemini summary contract, Twilio/dry-run behavior, approval-needed semantics, and bounded run-once visibility.
- `.planning/phases/AWXFYI-03.1-subscriber-fanout-and-daily-digests/03.1-CONTEXT.md` - Centralized subscriber/channel model, daily digest/no-change delivery, service-local timezone, and dedupe/failure handling.

### Existing Code Integration Points
- `src/main/kotlin/com/airwallexfyi/monitor/MonitorRunService.kt` - Existing canonical monitor orchestration; scheduler and CLI should call this path.
- `src/main/kotlin/com/airwallexfyi/monitor/MonitorRunResult.kt` - Current bounded run-result shape and status values for CLI logging/exit behavior.
- `src/main/kotlin/com/airwallexfyi/config/AppProperties.kt` - Existing scheduler, digest timezone, source, dry-run, Gemini, Twilio, WhatsApp, and admin config model.
- `src/main/resources/application.yml` - Existing environment variable bindings including scheduler enabled/fixed delay and dry-run defaults.
- `src/main/kotlin/com/airwallexfyi/AirwallexFyiApplication.kt` - Minimal Spring Boot application startup where runner/scheduling enablement will connect.
- `src/main/kotlin/com/airwallexfyi/admin/AdminController.kt` - Existing protected `/admin/run-once` and health endpoint showing scheduler status.
- `src/main/kotlin/com/airwallexfyi/digests/DailyDigestService.kt` - Existing daily subscriber digest/no-change fanout and delivery dedupe behavior.
- `src/main/kotlin/com/airwallexfyi/notifications/WhatsAppNotifier.kt` - Existing provider boundary for dry-run and Twilio notification delivery.

### Tests And Fixtures
- `src/test/kotlin/com/airwallexfyi/monitor/MonitorRunServiceTest.kt` - Integration-style monitor coverage for seed, dedupe, new summaries, digest fanout, failures, and approval-needed cases.
- `src/test/kotlin/com/airwallexfyi/sources/AirwallexSourceDiscoveryServiceTest.kt` - Sitemap filtering and fetch failure coverage.
- `src/test/kotlin/com/airwallexfyi/articles/ArticleExtractorTest.kt` - Structured extraction, HTML fallback, hash normalization, and fetch failure coverage.
- `src/test/kotlin/com/airwallexfyi/digests/DailyDigestServiceTest.kt` - Digest fanout, no-change, duplicate guard, timezone, and failure coverage.
- `src/test/resources/fixtures/airwallex/sitemap-blog.xml` - Existing fixture sitemap suitable for smoke/verification paths.
- `src/test/resources/fixtures/airwallex/blog-agentos.html` - Existing Blog fixture.
- `src/test/resources/fixtures/airwallex/newsroom-leapfin.html` - Existing Newsroom fixture.
- `build.gradle.kts` - Kotlin, Spring Boot, Flyway, JDBC, Jsoup, H2/Postgres, and JUnit test setup.

### Docs To Create
- `README.md` - Does not exist yet; Phase 4 should create it.
- `.env.example` - Does not exist yet; Phase 4 should create it without secrets.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MonitorRunService.runOnce()` already centralizes the exact behavior Phase 4 needs to schedule and expose from CLI.
- `MonitorRunResult` already contains bounded counts, sample URLs, sample payloads, digest delivery samples, error samples, status, and message fields suitable for CLI/admin output.
- `AppProperties.Scheduler` and `application.yml` already define `scheduler.enabled` and `scheduler.fixed-delay-ms`, but no production scheduler currently uses them.
- `AdminController.runOnce()` already proves the service path can be triggered manually over HTTP.
- Existing fixtures and fake-client test helpers provide enough infrastructure for repeatable smoke tests.

### Established Patterns
- Keep external effects behind small interfaces and fake them in tests.
- Keep local defaults safe: dry-run true, scheduler disabled, no real provider calls unless explicitly configured.
- Keep run results bounded with counts and small samples rather than unbounded payload dumps.
- Use Spring Boot configuration properties with environment variable bindings.
- Use Spring Data JDBC/Flyway/H2-in-PostgreSQL-mode tests rather than ad hoc persistence mocks.

### Integration Points
- Add a small scheduling component under the existing app package that delegates to `MonitorRunService.runOnce()` when scheduler config is enabled.
- Add a small one-shot startup runner that detects `--run-once`, runs the monitor once, logs the result, and exits without starting duplicate scheduled work.
- Extend or add tests near config/admin/monitor coverage rather than creating a separate framework.
- Add `README.md` and `.env.example` at repository root.

</code_context>

<specifics>
## Specific Ideas

- User wants the agent to choose the remaining Phase 4 decisions, but preserve the options and picks so they can change them later.
- The preferred architecture remains centralized: summaries are created once in the service database, and subscriber/channel delivery is fanout from that central state.
- Subscriber-facing behavior remains daily: send a new-post digest when eligible summaries exist, otherwise one no-change message per active channel per service-local day.
- Automated smoke verification should be deterministic and local; live network/provider checks can be documented as manual optional steps.

</specifics>

<deferred>
## Deferred Ideas

- Distributed scheduler lock or leader election for multi-instance deployment.
- Cron/time-of-day scheduling and per-subscriber timezone/preferences.
- Dockerfile, Compose, Railway/Render/Fly.io deployment guide, or CI/CD pipeline.
- Supabase/admin UI for subscribers.
- Slack delivery and chatbot interactions.
- Live end-to-end smoke test against real Airwallex/Gemini/Twilio.
- Automatic retry scheduling for failed subscriber deliveries.

</deferred>

---

*Phase: 4-Scheduling, Verification, And Deployment Readiness*
*Context gathered: 2026-06-22*
