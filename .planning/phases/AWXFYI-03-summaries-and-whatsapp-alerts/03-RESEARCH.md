# Phase 3: Summaries And WhatsApp Alerts - Research

**Researched:** 2026-06-21
**Status:** Complete

## Research Question

What needs to be known to plan Phase 3 well: strict Gemini JSON summaries, summary persistence, WhatsApp alert delivery through Twilio Sandbox, dry-run behavior, approval-needed handling, and bounded admin visibility.

## Inputs Read

- `.planning/phases/AWXFYI-03-summaries-and-whatsapp-alerts/03-CONTEXT.md`
- `.planning/REQUIREMENTS.md`
- `.planning/ROADMAP.md`
- `.planning/STATE.md`
- `.planning/phases/AWXFYI-02-airwallex-source-monitoring/02-CONTEXT.md`
- `AGENTS.md`
- `build.gradle.kts`
- `src/main/kotlin/com/airwallexfyi/config/AppProperties.kt`
- `src/main/kotlin/com/airwallexfyi/monitor/MonitorRunService.kt`
- `src/main/kotlin/com/airwallexfyi/monitor/PostStateService.kt`
- `src/main/kotlin/com/airwallexfyi/monitor/MonitorRunResult.kt`
- `src/main/kotlin/com/airwallexfyi/posts/PostRecord.kt`
- `src/main/kotlin/com/airwallexfyi/posts/PostRepository.kt`
- `src/main/kotlin/com/airwallexfyi/posts/ProcessingStatus.kt`
- `src/main/kotlin/com/airwallexfyi/admin/AdminController.kt`
- `src/main/kotlin/com/airwallexfyi/admin/AdminDtos.kt`
- `src/main/resources/db/migration/V1__initial_schema.sql`
- `src/main/resources/application.yml`

## Provider Findings

### Gemini

Official Gemini structured-output docs verified on 2026-06-21 show that Gemini can be asked for JSON conforming to a supplied schema using `responseFormat.text.mimeType = "application/json"` and a JSON schema. The REST examples use `generateContent` with an `x-goog-api-key` header and a model-specific endpoint under `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`.

Planning implication: implement a small Gemini client over Spring `RestClient`, keep the request/response DTOs local, and validate the response again with Jackson/Kotlin types. Do not trust the provider response without parsing and field checks.

Official pricing docs were checked for the selected free-tier direction, but exact free-tier quotas and model names may change. Keep the model configurable through environment variables and do not bake plan correctness into a single free-tier quota assumption.

### Twilio WhatsApp Sandbox

Official Twilio docs verified on 2026-06-21 show that the WhatsApp Sandbox can be activated for test messaging before production sender approval, and Twilio's Messages resource supports WhatsApp channel addresses using the `whatsapp:+E164` form for `From` and `To`. The Messages resource records statuses such as `queued`, `sent`, `failed`, `delivered`, and `undelivered`, and returns a message SID.

Planning implication: use a notifier interface and a Twilio REST implementation. Do not call Twilio in dry-run. Persist provider message ID and failure text in `notification_attempts` when available.

## Codebase Findings

### Existing Strengths

- `MonitorRunService.runOnce()` already owns the monitor pipeline and returns bounded counts/samples.
- `PostStateService` already classifies first-run seed, new URLs, changed known URLs, and skipped known URLs.
- `V1__initial_schema.sql` already includes `summaries` and `notification_attempts`, so Phase 3 can reintroduce only the Kotlin persistence types it needs.
- `ProcessingStatus` is currently small and can be extended with coarse lifecycle states.
- Existing tests use fake clients, H2 in PostgreSQL mode, MockMvc, and fixture HTML. Phase 3 should continue that pattern.
- `RestClient` is already used for Airwallex HTTP fetches, so Gemini/Twilio clients can avoid extra SDK dependencies.

### Current Gaps

- `AppProperties` still exposes `openai` settings, while the Phase 3 decision is Gemini first. Config should move to provider-neutral AI settings plus Gemini API key/model defaults.
- `PostRepository` only has `findByUrl`; summary/notification workflows need lookup by ID or simple repository/JDBC helpers.
- There are no Kotlin records/repositories for `summaries` or `notification_attempts` after the Ponytail cleanup. Reintroduce only active types.
- `MonitorRunResult` has no summary/alert counts yet and does not distinguish Gemini calls from Twilio calls.
- No manual approval endpoint exists for approval-needed summary work.

## Recommended Architecture

Use four vertical MVP slices:

1. Summary persistence contract and coarse post statuses.
2. Gemini strict JSON summarizer behind a provider interface.
3. WhatsApp payload formatter plus dry-run/Twilio notifier behind an interface.
4. Run-once/admin integration that auto-processes new posts and surfaces approval-needed work for seeded or changed posts.

## Validation Architecture

- Unit-test strict summary JSON parsing with valid JSON, missing required fields, wrong `source_type`, empty bullets, and malformed JSON.
- Unit-test Gemini request construction with a fake transport so tests do not call Google.
- Unit-test WhatsApp formatting deterministically from a saved summary and post URL.
- Unit-test dry-run notifier to prove it records/logs payloads and never calls Twilio.
- Unit-test Twilio notifier with fake transport to prove `From`, `To`, `Body`, and Basic Auth inputs are sent to the expected Messages endpoint shape.
- Integration-test monitor run with fake discovery/extraction/summarizer/notifier to prove new posts produce summary and dry-run alert counts.
- MockMvc-test admin run-once response and manual approval endpoint.
- Run `./gradlew.bat test` as the final phase-level verification.

## Risks And Mitigations

- Risk: Gemini schema support or model names shift. Mitigation: keep endpoint/model configurable and test request construction against the documented structured-output shape as of 2026-06-21.
- Risk: provider returns syntactically valid JSON that is semantically weak. Mitigation: enforce required fields, non-empty headline, 3-5 bullets, why-it-matters text, tags, and source type.
- Risk: historical seed posts trigger AI cost or alert spam. Mitigation: only auto-process `PostApplyKind.NEW`; mark seeded/missing-summary and updated known posts approval-needed.
- Risk: dry-run accidentally calls Twilio. Mitigation: separate notifier interface; dry-run implementation cannot reach the Twilio client and tests assert Twilio transport call count is zero.
- Risk: duplicate alerts. Mitigation: use `notification_attempts` unique `(post_id, channel, recipient)` constraint and check attempts before live sends.
- Risk: secrets leak through logs or admin responses. Mitigation: never include API keys, auth tokens, or full provider request headers in run-result samples; only show payload body previews and provider message IDs/errors.

## Planning Recommendation

Proceed with four plans in dependency order:

- 03-01 Summary persistence and lifecycle status contract.
- 03-02 Gemini strict JSON summarizer.
- 03-03 WhatsApp formatter, dry-run notifier, and Twilio sender.
- 03-04 Monitor/admin integration for auto-processing, approval-needed work, and bounded run-result visibility.

## RESEARCH COMPLETE
