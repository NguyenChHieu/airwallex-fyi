# Phase 2: Airwallex Source Monitoring - Context

**Gathered:** 2026-06-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 2 replaces the Phase 1 `MonitorRunService.runOnce()` stub with real source monitoring for public Airwallex global Blog and Newsroom articles. It discovers candidate URLs from the public Airwallex blog sitemap, extracts article metadata and body text, persists/dedupes posts through the existing Spring Data JDBC `posts` table, and reports run results through the protected admin surface.

Phase 2 does not call OpenAI, send Twilio/WhatsApp notifications, or add unattended scheduling. Those remain Phase 3 and Phase 4 work.

</domain>

<decisions>
## Implementation Decisions

### URL Discovery And Filtering
- **D-01:** Use strict global-only sitemap filtering. Accept only public article URLs under `/global/blog/...` and `/global/newsroom/...` from `https://www.airwallex.com/global/sitemap-blog.xml`.
- **D-02:** Exclude pagination and non-article paths such as `/global/newsroom/page/2`, regional duplicates, app/API/private paths, and anything outside the v1 Blog/Newsroom source boundary.
- **D-03:** Classify `sourceType` with a hybrid rule: URL path supplies the initial value (`blog` or `newsroom`), and extraction may confirm or enrich from page metadata when verified.
- **D-04:** If the sitemap fetch fails, keep the service usable. Return existing database state/counts, clearly report discovery failure, and do not write new post records for that failed run.

### Article Extraction Rules
- **D-05:** A usable article requires a title and meaningful body text. Publication date, description, and author are optional.
- **D-06:** Use structured `__NEXT_DATA__` first. Live verification on 2026-06-20 found Blog article data under `pageData.post.fields` and Newsroom article data under `pageData.pr.fields`; both use a structured `content` object.
- **D-07:** HTML/Jsoup fallback is only for missing or unparseable structured content. If fallback succeeds, store the usable post and make fallback usage visible in logs/run results.
- **D-08:** Parser behavior must not be assumed. Rules must be backed by live Airwallex page checks or captured fixtures. If Airwallex page shape changes, implementation should update fixtures/tests instead of silently guessing.
- **D-09:** Store normalized plain text as `article_body`. Flatten structured content by recursively collecting text nodes and preserving readable paragraph/heading spacing.
- **D-10:** Image URLs may be extracted opportunistically if cheap, but image/media extraction is not required in the Phase 2 persistence contract. Phase 3 summaries should use title, description, and body text.
- **D-11:** Compute the stable content hash from normalized title + description + body. Do not include noisy fields such as author image objects, read time, category payloads, or rich media metadata.
- **D-12:** If an article has title and body but no date, store it with `published_at = null`. Keep `sitemap_lastmod` and `discovered_at` as separate signals.
- **D-13:** Keep small representative fixtures for one verified Blog article and one verified Newsroom article so parser tests do not depend on live network availability.

### First-Run Seed And Dedupe
- **D-14:** On a fresh database, first run seeds only a configurable recent baseline rather than the full sitemap history. Default baseline size is 25 most recent posts.
- **D-15:** After seeding, `new` means URL not already present in the database. Existing URLs are not treated as new even if their content hash changes.
- **D-16:** If a known URL content hash changes, update metadata/body/hash quietly, keep it non-new, and expose the change through `updatedCount` or equivalent run-result visibility.
- **D-17:** If one article extraction fails, report the failed URL/reason, skip writing that post, and continue processing the rest of the run.
- **D-18:** Keep Postgres/Spring Data JDBC for Phase 2. MongoDB is not needed for this monitor because URL uniqueness, summary uniqueness, and notification dedupe are relational workflow constraints. Add JSON/JSONB columns later only if raw payload retention becomes valuable.

### Run Result And Admin Visibility
- **D-19:** `/admin/run-once` should return operational counts plus small samples: status, sitemap fetch status, discovered count, seeded count, new count, updated count, skipped count, failed count, and a few sample URLs/errors.
- **D-20:** Use explicit statuses such as `completed`, `partial_failure`, and `failed` so partial failures are not hidden.
- **D-21:** Add simple filters to `/admin/posts/recent` when cleanly scoped, such as `sourceType` and/or `processingStatus`, to inspect seeded/new/updated/fallback behavior without a dashboard.
- **D-22:** Admin responses should expose short body previews only, not full article bodies by default.
- **D-23:** Full detailed errors go to application logs for now. The endpoint returns sampled errors only; Phase 2 should not add DB persistence for every failed extraction detail.
- **D-24:** Do not add Phase 2 dry-run for discovery/extraction. This phase has no external notification side effects, and database writes are necessary to test dedupe behavior.

### the agent's Discretion
- The planner may choose the exact package/class names and internal DTO shapes as long as they fit existing package-by-capability style and the decisions above.
- The planner may decide whether fallback visibility is represented as a run-result flag, log field, status value, or small DTO note, as long as it is visible without broad schema churn.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project And Phase Scope
- `.planning/PROJECT.md` - Project purpose, v1 source policy, out-of-scope boundaries, and public source notes.
- `.planning/REQUIREMENTS.md` - Phase 2 mapped requirements: SRC-01, SRC-02, SRC-03, EXT-01, EXT-02, EXT-03, STATE-02, STATE-03.
- `.planning/ROADMAP.md` - Phase 2 goal and success criteria.
- `.planning/phases/AWXFYI-01-service-skeleton-and-persistence/01-CONTEXT.md` - Carried-forward stack, persistence, and admin/run-once decisions.

### Existing Research
- `.planning/research/SUMMARY.md` - Linear monitor pipeline and source-monitoring priorities.
- `.planning/research/ARCHITECTURE.md` - Sitemap -> extraction -> dedupe data flow.
- `.planning/research/PITFALLS.md` - Avoid brittle CSS scraping, historical spam, and overbuilt agent architecture.
- `.planning/research/FEATURES.md` - Table-stakes monitor capabilities and anti-features for v1.

### Existing Code Integration Points
- `src/main/kotlin/com/airwallexfyi/monitor/MonitorRunService.kt` - Phase 1 stub to replace with real run-once behavior.
- `src/main/kotlin/com/airwallexfyi/admin/AdminController.kt` - Protected admin endpoint currently delegates `/admin/run-once` and serves recent posts.
- `src/main/kotlin/com/airwallexfyi/posts/PostRecord.kt` - Existing post persistence record and available columns.
- `src/main/kotlin/com/airwallexfyi/posts/PostRepository.kt` - Existing URL lookup and recent-post repository methods.
- `src/main/resources/db/migration/V1__initial_schema.sql` - Existing schema constraints and post fields.

### Verified Public Airwallex Sources
- `https://www.airwallex.com/global/sitemap-blog.xml` - Public sitemap source for Phase 2 discovery. Live check on 2026-06-20 found pagination URLs as well as article URLs, so filtering must exclude `/page/` paths.
- `https://www.airwallex.com/global/blog/introducing-airwallex-agentos-manage-your-financial-operations-in-your-preferred-agent-environment` - Verified Blog article sample. Raw HTML contains `__NEXT_DATA__`; article fields were found under `pageData.post.fields`.
- `https://www.airwallex.com/global/newsroom/airwallex-acquires-leapfin-expanding-financial-lifecycle-capabilities` - Verified Newsroom article sample. Raw HTML contains `__NEXT_DATA__`; article fields were found under `pageData.pr.fields`.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MonitorRunService.runOnce()` - Current Phase 1 stub response should become the orchestration point for sitemap fetch, extraction, persistence, and run-result assembly.
- `PostRepository.findByUrl(url)` - Existing dedupe lookup for URL-based new/known classification.
- `PostRepository.findTop20ByOrderByDiscoveredAtDesc()` - Existing recent-post query can be extended or complemented for admin filters.
- `PostRecord` - Already has fields for URL, source type, title, description, author, published date, sitemap lastmod, discovered timestamp, content hash, article body, and processing status.

### Established Patterns
- Package by capability under `com.airwallexfyi`.
- Spring Data JDBC records with explicit `@Column` mappings and UUID identifiers.
- Safe local defaults and protected admin endpoints from Phase 1.
- MockMvc/Spring Boot tests for admin flows and H2/Flyway-backed persistence tests.

### Integration Points
- `/admin/run-once` should return the new Phase 2 run result instead of the Phase 1 stub.
- `/admin/posts/recent` may gain low-scope filters and body previews.
- The monitor pipeline should persist through `PostRepository` and keep later summary/notification tables untouched until Phase 3.

</code_context>

<specifics>
## Specific Ideas

- Prefer a simple, learnable pipeline over a broad crawler: sitemap candidates -> strict URL filter -> structured extraction -> fallback if needed -> URL dedupe -> persist/update posts -> run-result DTO.
- Do not rely on live network tests for parser correctness. Capture small fixtures from verified Blog and Newsroom pages during implementation.
- The first-run baseline should be recent and practical, not the entire current sitemap history: default 25 most recent posts, configurable later.
- User explicitly wants us to double-check page-shape assumptions. Treat live verification and fixtures as part of the implementation discipline.

</specifics>

<deferred>
## Deferred Ideas

- OpenAI summarization and WhatsApp/Twilio notification behavior remain Phase 3.
- Daily unattended scheduled checks and command-line `--run-once` remain Phase 4.
- Full rich content preservation, image/media persistence, raw structured payload storage, and dashboard inspection can be revisited later if needed.
- Slack, digest mode, external news sources, regional sources, and historical Q&A remain v2/future scope.

</deferred>

---

*Phase: 2-Airwallex Source Monitoring*
*Context gathered: 2026-06-20*
