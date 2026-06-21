---
phase: AWXFYI-03-summaries-and-whatsapp-alerts
plan: "02"
subsystem: summaries
tags: [kotlin, spring-boot, gemini, structured-json]
requires:
  - phase: AWXFYI-03-summaries-and-whatsapp-alerts
    plan: "01"
    provides: StructuredSummary, SummaryRecord, SummaryRepository
provides:
  - Provider-neutral AI config with Gemini as the MVP provider
  - Fakeable Gemini REST transport
  - Strict Gemini structured JSON summary client
  - ArticleSummaryService that persists only validated summaries
  - Offline tests for request shape, parsing failures, and persistence behavior
key-files:
  created:
    - src/main/kotlin/com/airwallexfyi/summaries/AiSummaryClient.kt
    - src/main/kotlin/com/airwallexfyi/summaries/GeminiTransport.kt
    - src/main/kotlin/com/airwallexfyi/summaries/GeminiSummaryClient.kt
    - src/main/kotlin/com/airwallexfyi/summaries/ArticleSummaryService.kt
    - src/test/kotlin/com/airwallexfyi/summaries/GeminiSummaryClientTest.kt
    - src/test/kotlin/com/airwallexfyi/summaries/ArticleSummaryServiceTest.kt
  modified:
    - src/main/kotlin/com/airwallexfyi/config/AppProperties.kt
    - src/main/resources/application.yml
    - src/main/kotlin/com/airwallexfyi/summaries/StructuredSummary.kt
    - src/test/kotlin/com/airwallexfyi/config/AppPropertiesTest.kt
requirements-completed: [AI-01]
completed: 2026-06-21
---

# Phase AWXFYI-03 Plan 02: Gemini Strict JSON Summarizer Summary

## Accomplishments

- Replaced stale OpenAI config with `airwallex-fyi.ai.*` plus `airwallex-fyi.gemini.api-key` defaults.
- Added `AiSummaryClient`, `GeminiTransport`, and `GeminiSummaryClient` using the verified Gemini REST structured-output shape: `contents`, `generationConfig.responseFormat.text.mimeType = application/json`, schema, and `x-goog-api-key` in the transport.
- Added strict local parsing from Gemini candidate text into `StructuredSummary.validated(...)`.
- Added `ArticleSummaryService` to save summary rows with model and `gemini-summary-v1` prompt version only after validation succeeds.
- Kept tests offline with fake Gemini transport and fake summary clients.

## Task Commits

| Commit | Description |
|--------|-------------|
| e21a58c | Add Gemini config, transport, strict summary client, summary service, and tests. |

## Deviations from Plan

- Changed `StructuredSummary` from a private-constructor data class to a private-constructor regular class with explicit equality, avoiding Kotlin's generated `copy()` exposure warning while preserving value-like test behavior.
- Used index-based Jackson array traversal because the available Jackson 3 `JsonNode` API did not expose the older `elements()` helper in this setup.

## Verification

- `./gradlew.bat test --tests "*AppPropertiesTest" --tests "*GeminiSummaryClientTest" --tests "*ArticleSummaryServiceTest"` - passed.
- `./gradlew.bat test` - passed.

## User Setup Required

Set `GEMINI_API_KEY` before live summarization. `AI_MODEL` defaults to `gemini-3.5-flash` and can be overridden.

## Self-Check: PASSED

Plan 03-02 success criteria are met: Gemini is behind an interface, structured output is parsed and validated locally, invalid provider output cannot persist, and summary persistence happens only after validation.
