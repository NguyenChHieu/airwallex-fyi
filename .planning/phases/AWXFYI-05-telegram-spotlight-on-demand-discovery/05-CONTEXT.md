# Phase 5: Telegram Spotlight on-demand discovery - Context

**Gathered:** 2026-07-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 5 adds one Telegram command, `/spotlight`, that surfaces a randomly selected post from a bounded recent Airwallex Blog/Newsroom pool. It reuses the service-owned post and summary database, fetches and summarizes only the selected article when data is missing, and returns a useful summary plus direct source link.

This phase does not add semantic search, importance scoring, per-user recommendation history, category preferences, a new scheduler, a full sitemap run per command, or a new notification channel.

</domain>

<decisions>
## Implementation Decisions

### Command And Selection
- **D-01:** Name the command `/spotlight`; update `/help` to describe it.
- **D-02:** Select uniformly from the 25 most recent canonical Blog/Newsroom posts, ordered by publication date with sitemap/discovery timestamps as fallbacks.
- **D-03:** Do not add per-user repeat history in v1. Repeats are acceptable and can be addressed after real usage.
- **D-04:** `/spotlight` is read/on-demand behavior; it does not subscribe the caller and does not invoke `MonitorRunService.runOnce()` or `DailyDigestService`.

### Summary Reuse And Backfill
- **D-05:** Reuse an existing current summary without calling Gemini.
- **D-06:** If the selected post has no usable body, fetch and extract only that post URL, then persist the hydrated canonical fields while preserving its processing status.
- **D-07:** If the summary is missing, generate it once through `ArticleSummaryService` and persist it in the existing `summaries` table.
- **D-08:** If content is marked `APPROVAL_NEEDED`, the explicit `/spotlight` request authorizes replacement of the stale summary for the selected post.
- **D-09:** Preserve `SEEDED` and `BASELINED` status after historical backfill so `DigestEligibilityService` does not treat that summary as a newly published update. A successfully recovered `DISCOVERED`, `SUMMARY_FAILED`, or `APPROVAL_NEEDED` post may move to `SUMMARY_READY`.
- **D-10:** No database migration is needed; canonical summary uniqueness and processing status already provide the required persistence and digest boundary.

### Telegram Experience And Failure Handling
- **D-11:** Send a short acknowledgement before extraction/Gemini work so the user sees immediate progress.
- **D-12:** Format one Spotlight response with source type, headline, 3-5 bullets, why it matters, and a direct `Read:` link.
- **D-13:** On extraction or Gemini failure, send a friendly failure message that still includes the selected article URL.
- **D-14:** Keep existing allowlist and webhook update dedupe behavior unchanged.

### the agent's Discretion
- Exact service/package names, random-number API, SQL-vs-in-memory bounded selection, and concise copy may follow existing Kotlin/Spring patterns.
- Focused tests may use one-candidate pools for deterministic behavior rather than introducing a random-provider abstraction solely for testing.

</decisions>

<canonical_refs>
## Canonical References

### Telegram Command Flow
- `src/main/kotlin/com/airwallexfyi/subscribers/TelegramWebhookController.kt` - Authenticated webhook entrypoint.
- `src/main/kotlin/com/airwallexfyi/subscribers/TelegramSubscriptionService.kt` - Current command routing, allowlist, update dedupe, and replies.
- `src/test/kotlin/com/airwallexfyi/subscribers/TelegramSubscriptionServiceTest.kt` - Command and concurrent webhook retry coverage.

### Posts, Extraction, And Summaries
- `src/main/kotlin/com/airwallexfyi/posts/PostRecord.kt` - Canonical article state and processing status.
- `src/main/kotlin/com/airwallexfyi/posts/PostRepository.kt` - Existing post persistence boundary.
- `src/main/kotlin/com/airwallexfyi/articles/ArticleExtractor.kt` - Public single-article extraction path.
- `src/main/kotlin/com/airwallexfyi/monitor/PostStateService.kt` - Canonical post field updates and status handling.
- `src/main/kotlin/com/airwallexfyi/summaries/ArticleSummaryService.kt` - Gemini-backed canonical summary creation/replacement.
- `src/main/kotlin/com/airwallexfyi/summaries/SummaryRepository.kt` - One summary per post lookup.

### Digest Safety And Formatting
- `src/main/kotlin/com/airwallexfyi/digests/DigestEligibilityService.kt` - Only `SUMMARY_READY` posts enter scheduled digests.
- `src/main/kotlin/com/airwallexfyi/digests/LatestUpdatesService.kt` - Existing readable Telegram summary layout and ordering conventions.
- `src/main/kotlin/com/airwallexfyi/notifications/MessageBodyLimits.kt` - Telegram provider message limit.

</canonical_refs>

<deferred>
## Deferred Ideas

- Per-user no-repeat history.
- `/spotlight blog` and `/spotlight newsroom` filters.
- Importance scoring, personalization, and reactions.
- Durable background job queue or multi-instance single-flight summary generation.
- Historical Q&A and semantic search.

</deferred>

---

*Phase: 5-Telegram Spotlight on-demand discovery*
*Context gathered: 2026-07-14*
