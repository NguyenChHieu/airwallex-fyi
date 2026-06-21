# Phase 3: Summaries And WhatsApp Alerts - Context

**Gathered:** 2026-06-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 3 turns newly discovered public Airwallex Blog and Newsroom articles into structured AI summaries and WhatsApp alert payloads. It adds the AI summarization provider path, summary persistence, alert formatting, Twilio Sandbox notification sending behind an interface, dry-run notification behavior, and operational visibility through the existing admin run-once response and recent-post status filters.

Phase 3 must not build unattended scheduling, command-line run-once execution, Slack, digest mode, production WhatsApp Business templates, full dashboard approval UI, or historical Q&A. Those remain Phase 4 or future scope.

</domain>

<decisions>
## Implementation Decisions

### AI Provider And Summary Contract
- **D-01:** Target Gemini free tier first for the MVP AI provider, because the user wants a free hosted AI option. Keep the summarizer provider-backed so the app is not hard-coded around one vendor forever.
- **D-02:** Use strict structured JSON only. Required fields are `headline`, `bullets`, `why_it_matters`, `tags`, and `source_type`.
- **D-03:** If the AI response is invalid JSON or missing required fields, record a summary failure and do not send WhatsApp for that article.
- **D-04:** Phase 3 should summarize from the extracted title, description, source type, direct URL, and normalized article body. Phase 2 deliberately did not keep image/media extraction in the persistence contract.
- **D-05:** Do not assume Gemini API behavior. Planning and implementation must verify the current official Gemini API docs for JSON or schema-constrained output before coding the client.

### Summary Eligibility And Approval Needs
- **D-06:** Auto-summarize newly discovered posts created as `DISCOVERED` by the source monitor.
- **D-07:** Do not auto-call AI for seeded/history posts that are missing summaries. Surface them as approval-needed work, with status/reason such as `missing_summary`.
- **D-08:** When a known URL content hash changes, surface it as approval-needed work, with status/reason such as `content_changed`. Re-summarization may happen only after user approval.
- **D-09:** Changed known URLs must not create duplicate WhatsApp alerts. Avoid duplicate alerts even if a manual re-summary is later added.
- **D-10:** The approval flow should be lightweight for this phase. It may be represented through admin status/run-result visibility and simple protected admin actions only if the planner can keep it small. A full approval dashboard is out of scope.

### WhatsApp Alert Format
- **D-11:** Use a detailed but still concise WhatsApp format: headline, 3-5 short bullets, why it matters, tags, source type, and the direct source link.
- **D-12:** Alerts must include the direct Airwallex source link so the user can inspect the original post quickly.
- **D-13:** Keep formatting deterministic enough to test. The alert formatter should be a separate component from the AI summarizer and Twilio sender.

### Dry-Run And Notification Sending
- **D-14:** When `DRY_RUN=true`, run the full pipeline except Twilio: call Gemini, parse and save the summary, build the exact WhatsApp payload, log/return a payload preview, and record notification status as dry-run/skipped.
- **D-15:** Dry-run must never call Twilio.
- **D-16:** Twilio Sandbox sending must live behind a notifier interface so tests and dry-run do not depend on the real provider.
- **D-17:** If Gemini succeeds but Twilio fails, record the failed notification attempt and surface it in the run result/admin state. Do not add automatic retry machinery in Phase 3.

### Processing State And Admin Visibility
- **D-18:** Use simple lifecycle statuses on `posts.processing_status`, such as `SUMMARY_READY`, `ALERT_SENT`, `DRY_RUN_READY`, `SUMMARY_FAILED`, `ALERT_FAILED`, and `APPROVAL_NEEDED`.
- **D-19:** Keep status values coarse. They should make `/admin/posts/recent` useful without turning post status into a detailed workflow engine.
- **D-20:** `/admin/run-once` should keep the existing counts-and-samples style, extended with summary and alert counts, sample URLs, payload previews, and sampled errors.
- **D-21:** Do not return every summary, every alert payload, or every error in `/admin/run-once`; keep response size bounded.

### the agent's Discretion
- The planner may choose exact package names, DTO names, repository shapes, and whether manual approval is implemented as a small protected endpoint in Phase 3 or deferred, as long as approval-needed items are visible and no AI call is made for seeded/history or changed known posts without approval.
- The planner may choose whether to reintroduce Spring Data JDBC repositories for `summaries` and `notification_attempts` or use `NamedParameterJdbcTemplate`, as long as the implementation stays simple and testable.
- The planner may add a small migration only if the existing `V1__initial_schema.sql` cannot safely support the selected statuses or approval-needed visibility.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project And Phase Scope
- `.planning/PROJECT.md` - Project purpose, constraints, v1 source policy, key decisions, and out-of-scope boundaries.
- `.planning/REQUIREMENTS.md` - Phase 3 mapped requirements: AI-01, NOTIF-01, and NOTIF-02.
- `.planning/ROADMAP.md` - Phase 3 goal and success criteria.
- `.planning/phases/AWXFYI-01-service-skeleton-and-persistence/01-CONTEXT.md` - Carried-forward stack, environment-backed config, admin endpoint, and persistence decisions.
- `.planning/phases/AWXFYI-02-airwallex-source-monitoring/02-CONTEXT.md` - Carried-forward discovery, extraction, dedupe, first-run seed, content-change, and run-result decisions.

### Existing Research
- `.planning/research/SUMMARY.md` - Linear monitor pipeline and source-monitoring priorities.
- `.planning/research/ARCHITECTURE.md` - Sitemap to extraction to dedupe flow that Phase 3 extends.
- `.planning/research/PITFALLS.md` - Avoid brittle overbuilt agent architecture and historical spam.
- `.planning/research/FEATURES.md` - Table-stakes monitor capabilities and v1 anti-features.

### Existing Code Integration Points
- `src/main/kotlin/com/airwallexfyi/monitor/MonitorRunService.kt` - Current run-once orchestration for discovery, extraction, persistence, and run-result assembly.
- `src/main/kotlin/com/airwallexfyi/monitor/PostStateService.kt` - Existing first-run seed, new-post, and known-URL update classification rules.
- `src/main/kotlin/com/airwallexfyi/monitor/MonitorRunResult.kt` - Existing counts/samples response shape to extend with summary and notification visibility.
- `src/main/kotlin/com/airwallexfyi/posts/PostRecord.kt` - Existing post persistence record and `processing_status` field.
- `src/main/kotlin/com/airwallexfyi/posts/PostRepository.kt` - Existing post lookup repository.
- `src/main/kotlin/com/airwallexfyi/admin/AdminController.kt` - Existing protected `/admin/run-once` and `/admin/posts/recent` integration surface.
- `src/main/kotlin/com/airwallexfyi/config/AppProperties.kt` - Existing OpenAI/Twilio/WhatsApp/dry-run config placeholders that may need Gemini provider config updates.
- `src/main/resources/db/migration/V1__initial_schema.sql` - Existing `posts`, `summaries`, and `notification_attempts` tables.
- `src/main/resources/application.yml` - Existing environment variable defaults for AI, Twilio, WhatsApp recipient, dry-run, and source settings.

### Provider Documentation To Verify During Planning
- `https://ai.google.dev/gemini-api/docs` - Current Gemini API docs. Verify structured output and auth behavior before implementing.
- `https://ai.google.dev/gemini-api/docs/pricing` - Current Gemini API pricing/free-tier behavior. Verify before relying on free-tier assumptions.
- `https://www.twilio.com/docs/whatsapp/sandbox` - Current Twilio WhatsApp Sandbox setup and sending requirements. Verify before implementing the sender.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MonitorRunService.runOnce()` already orchestrates sitemap discovery, article extraction, post state application, and run-result assembly. Phase 3 should extend this path after new post persistence, not create a separate runner.
- `PostStateService.planWork()` already classifies first-run seed, new posts, update checks, and skipped known URLs. Phase 3 should reuse that classification rather than rediscovering eligibility from scratch.
- `PostRecord.processingStatus` and `/admin/posts/recent` filters already provide an operator-visible status surface.
- The existing schema already includes `summaries` and `notification_attempts`; Kotlin records/repositories were removed because they were unused, so Phase 3 should reintroduce only the pieces it actually uses.
- Existing tests use Spring Boot, H2 in PostgreSQL mode, MockMvc, fixture HTML, and small fake clients. Phase 3 should continue that style with fake AI/notifier clients.

### Established Patterns
- Package by capability under `com.airwallexfyi`.
- Keep external side effects behind small interfaces so tests can use fakes.
- Environment-backed settings only; no secrets in committed files.
- Safe local defaults: dry-run true, scheduler disabled, no provider calls unless explicitly configured by the run path.
- Admin responses expose bounded samples and previews, not unbounded body or payload dumps.

### Integration Points
- The summary pipeline connects after `PostApplyKind.NEW` records a new article and before alert sending.
- Approval-needed detection connects to seeded/history missing summaries and known URL content changes.
- Notification attempts connect to the existing `notification_attempts` table and should enforce one alert per post/channel/recipient.
- Dry-run payload previews should appear in logs and bounded run-result samples.

</code_context>

<specifics>
## Specific Ideas

- The first Phase 3 provider target is Gemini free tier, not OpenAI, but the internal interface should leave room for future OpenAI or local providers.
- The user wants us to double-check provider behavior from current official docs before implementation instead of assuming API details from memory.
- The user explicitly corrected the eligibility rule: new posts auto-summarize, while missing-summary and content-changed known posts should be surfaced with approval-needed status and require permission before summary/re-summary.
- Detailed WhatsApp alerts are preferred over ultra-short messages, but the bullets should stay short enough to feel like a useful alert rather than an article rewrite.

</specifics>

<deferred>
## Deferred Ideas

- Full approval dashboard for missing-summary/content-changed posts.
- Automatic retry scheduling for failed Twilio sends.
- Slack notifications and digest mode.
- Production WhatsApp Business sender/template approval.
- Scheduling and command-line run-once execution remain Phase 4.
- Historical Q&A over summaries remains future scope.

</deferred>

---

*Phase: 3-Summaries And WhatsApp Alerts*
*Context gathered: 2026-06-21*
