---
phase: AWXFYI-02-airwallex-source-monitoring
plan: "02"
subsystem: articles
tags: [kotlin, jackson, jsoup, contentful-rich-text, sha-256]
requires:
  - phase: AWXFYI-02-01
    provides: SitemapEntry, SourceType, AirwallexHttpClient
provides:
  - Structured-first Blog and Newsroom article extraction
  - Contentful rich-text flattening to normalized plain text
  - HTML fallback extraction with explicit extraction source
  - Stable content hashing from title, description, and body
affects: [monitor-run, persistence, admin-visibility]
tech-stack:
  added: []
  patterns: [structured-first extraction, fixture-backed parser tests, stable content hashing]
key-files:
  created:
    - src/main/kotlin/com/airwallexfyi/articles/ExtractedArticle.kt
    - src/main/kotlin/com/airwallexfyi/articles/RichTextFlattener.kt
    - src/main/kotlin/com/airwallexfyi/articles/ArticleExtractor.kt
    - src/main/kotlin/com/airwallexfyi/articles/ContentHashService.kt
    - src/test/kotlin/com/airwallexfyi/articles/ArticleExtractorTest.kt
    - src/test/kotlin/com/airwallexfyi/articles/ContentHashServiceTest.kt
    - src/test/resources/fixtures/airwallex/blog-agentos.html
    - src/test/resources/fixtures/airwallex/newsroom-leapfin.html
    - src/test/resources/fixtures/airwallex/fallback-article.html
  modified: []
key-decisions:
  - "Use Jackson tree parsing over rigid payload DTOs for Airwallex __NEXT_DATA__ content."
  - "Return extractionSource so fallback extraction is visible to later monitor/admin run results."
  - "Keep contentHash limited to normalized title, description, and body."
patterns-established:
  - "Article parser tests use captured representative fixtures, not live Airwallex network calls."
  - "Extractor wraps failures in ArticleExtractionException with URL and reason."
requirements-completed: [EXT-01, EXT-02, EXT-03]
duration: 30 min
completed: 2026-06-20
---

# Phase AWXFYI-02 Plan 02: Article Extraction and Content Hashing Summary

**Structured Airwallex Blog/Newsroom extraction with rich-text flattening, HTML fallback, and stable content hashes**

## Performance

- **Duration:** 30 min
- **Started:** 2026-06-20T23:12:00Z
- **Completed:** 2026-06-20T23:17:45Z
- **Tasks:** 4
- **Files modified:** 9

## Accomplishments

- Added representative Blog, Newsroom, and fallback HTML fixtures matching the verified Airwallex shapes.
- Added ExtractedArticle, ExtractionSource, and URL-aware ArticleExtractionException.
- Added Contentful-style rich-text flattening for headings, paragraphs, links, and list items.
- Added structured-first extraction from pageData.post.fields and pageData.pr.fields, with fallback HTML extraction.
- Added SHA-256 content hashing over normalized title, description, and body only.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add representative fixtures** - 316cbed (test)
2. **Task 2: Build extraction DTOs and rich text flattening** - e4a67a5 (feat)
3. **Task 3: Implement structured-first article extraction** - 5acf115 (feat)
4. **Task 4: Add stable content hashing** - 47d6c3f (feat)

**Plan metadata:** pending in this summary commit.

## Files Created/Modified

- src/main/kotlin/com/airwallexfyi/articles/ExtractedArticle.kt - Article DTO, extraction source enum, and extraction exception.
- src/main/kotlin/com/airwallexfyi/articles/RichTextFlattener.kt - Contentful rich-text to plain-text flattener.
- src/main/kotlin/com/airwallexfyi/articles/ArticleExtractor.kt - Structured-first extractor and HTML fallback.
- src/main/kotlin/com/airwallexfyi/articles/ContentHashService.kt - Stable normalized SHA-256 content hash service.
- src/test/kotlin/com/airwallexfyi/articles/ArticleExtractorTest.kt - Blog, Newsroom, fallback, date, failure, and media-hash tests.
- src/test/kotlin/com/airwallexfyi/articles/ContentHashServiceTest.kt - Hash normalization and sensitivity tests.
- src/test/resources/fixtures/airwallex/blog-agentos.html - Verified-shape Blog fixture.
- src/test/resources/fixtures/airwallex/newsroom-leapfin.html - Verified-shape Newsroom fixture.
- src/test/resources/fixtures/airwallex/fallback-article.html - HTML fallback fixture.

## Decisions Made

- Used Jackson tree parsing to avoid hard-coding the full Next.js payload shape beyond the verified article field paths.
- Added ExtractionSource so later monitor/admin responses can show when fallback was used.
- Kept image URLs opportunistic and excluded from contentHash, matching Phase 2 persistence scope.

## Deviations from Plan

None - plan executed as written. The plan referenced ProcessingStatus.kt in ead_first, but that file does not exist yet and was not needed for extraction behavior; status modeling remains in the persistence/admin waves.

## Issues Encountered

- Jackson 3 emits compile warnings for JsonNode.asText(), but the tests pass and the extractor is functional. This can be cleaned up later if the warnings become distracting.

## Verification

- .\gradlew.bat test --tests "*ArticleExtractorTest" --tests "*ContentHashServiceTest" - passed.
- .\gradlew.bat test - passed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Wave 3 can combine sitemap discovery and article extraction to seed posts, dedupe by URL, update changed content by hash, and report partial failures.

---
*Phase: AWXFYI-02-airwallex-source-monitoring*
*Completed: 2026-06-20*
