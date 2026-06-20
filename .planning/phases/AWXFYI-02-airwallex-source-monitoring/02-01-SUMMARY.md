---
phase: AWXFYI-02-airwallex-source-monitoring
plan: "01"
subsystem: sources
tags: [kotlin, spring-boot, jsoup, sitemap, restclient]
requires:
  - phase: AWXFYI-01-service-skeleton-and-persistence
    provides: Spring Boot app scaffold, AppProperties, admin/test foundation
provides:
  - Source config for Airwallex sitemap URL and first-run seed limit
  - Fakeable Airwallex HTTP client backed by Spring RestClient
  - Strict public sitemap discovery for global Blog and Newsroom URLs
affects: [source-monitoring, article-extraction, monitor-run]
tech-stack:
  added: [org.jsoup:jsoup:1.22.2]
  patterns: [interface-backed HTTP client, strict URL allowlist, fixture-only source tests]
key-files:
  created:
    - src/main/kotlin/com/airwallexfyi/posts/SourceType.kt
    - src/main/kotlin/com/airwallexfyi/sources/AirwallexHttpClient.kt
    - src/main/kotlin/com/airwallexfyi/sources/SitemapEntry.kt
    - src/main/kotlin/com/airwallexfyi/sources/AirwallexSourceDiscoveryService.kt
    - src/test/kotlin/com/airwallexfyi/sources/AirwallexSourceDiscoveryServiceTest.kt
    - src/test/resources/fixtures/airwallex/sitemap-blog.xml
  modified:
    - build.gradle.kts
    - src/main/kotlin/com/airwallexfyi/config/AppProperties.kt
    - src/main/resources/application.yml
    - src/test/kotlin/com/airwallexfyi/config/AppPropertiesTest.kt
key-decisions:
  - "Use SourceType enum for typed Blog and Newsroom candidates because the plan acceptance criteria referenced SourceType.BLOG."
  - "Build RestClient internally in the component so Spring context tests do not require a RestClient.Builder bean."
patterns-established:
  - "Airwallex outbound HTTP goes through a fakeable AirwallexHttpClient interface."
  - "Sitemap discovery uses a strict www.airwallex.com global Blog/Newsroom URL allowlist."
requirements-completed: [SRC-01, SRC-02, SRC-03]
duration: 25 min
completed: 2026-06-20
---

# Phase AWXFYI-02 Plan 01: Source Discovery and Configuration Summary

**Strict Airwallex public sitemap discovery with typed Blog/Newsroom candidates and configurable seed settings**

## Performance

- **Duration:** 25 min
- **Started:** 2026-06-20T23:00:00Z
- **Completed:** 2026-06-20T23:11:00Z
- **Tasks:** 3
- **Files modified:** 10

## Accomplishments

- Added source configuration for the verified public Airwallex sitemap and default first-run seed limit of 25.
- Added a fakeable AirwallexHttpClient interface with a Spring RestClient implementation and explicit User-Agent.
- Added strict sitemap discovery that accepts only /global/blog/{slug} and /global/newsroom/{slug} from www.airwallex.com.
- Added fixture-based tests for accepted URLs, rejected pagination/private/regional URLs, missing lastmod, and fetch failure propagation.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add source configuration** - 32530b2 (chore)
2. **Task 2: Add public HTTP client wrapper** - eb0a644 (feat)
3. **Task 3: Implement strict sitemap discovery** - 6e991ad (feat)

**Plan metadata:** pending in this summary commit.

## Files Created/Modified

- uild.gradle.kts - Adds Jsoup 1.22.2 for XML/HTML parsing.
- src/main/kotlin/com/airwallexfyi/config/AppProperties.kt - Adds typed source config.
- src/main/resources/application.yml - Adds env-backed source defaults.
- src/main/kotlin/com/airwallexfyi/posts/SourceType.kt - Adds Blog/Newsroom enum.
- src/main/kotlin/com/airwallexfyi/sources/AirwallexHttpClient.kt - Adds fakeable HTTP fetch interface and RestClient implementation.
- src/main/kotlin/com/airwallexfyi/sources/SitemapEntry.kt - Adds typed discovered sitemap candidate DTO.
- src/main/kotlin/com/airwallexfyi/sources/AirwallexSourceDiscoveryService.kt - Adds strict sitemap parsing and filtering.
- src/test/kotlin/com/airwallexfyi/config/AppPropertiesTest.kt - Adds source config binding/default/validation tests.
- src/test/kotlin/com/airwallexfyi/sources/AirwallexSourceDiscoveryServiceTest.kt - Adds fixture-driven discovery tests.
- src/test/resources/fixtures/airwallex/sitemap-blog.xml - Adds accepted/rejected sitemap URL examples.

## Decisions Made

- Added SourceType.kt even though it was omitted from iles_modified, because the plan and acceptance criteria explicitly require SourceType.BLOG and SourceType.NEWSROOM.
- Built RestClient inside RestClientAirwallexHttpClient instead of injecting RestClient.Builder, after full Spring context tests proved no builder bean exists in this setup.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added SourceType.kt**
- **Found during:** Task 3 (Strict sitemap discovery)
- **Issue:** The plan referenced SourceType.BLOG, but the repository did not contain a SourceType enum and the plan did not list it in iles_modified.
- **Fix:** Added src/main/kotlin/com/airwallexfyi/posts/SourceType.kt with BLOG and NEWSROOM values.
- **Files modified:** src/main/kotlin/com/airwallexfyi/posts/SourceType.kt
- **Verification:** AirwallexSourceDiscoveryServiceTest asserts typed Blog and Newsroom candidates.
- **Committed in:** 6e991ad

**2. [Rule 3 - Blocking] Removed missing RestClient.Builder bean dependency**
- **Found during:** Full test suite verification
- **Issue:** Spring context tests failed because no RestClient.Builder bean was available.
- **Fix:** Changed the RestClient component to build its own client with RestClient.builder().
- **Files modified:** src/main/kotlin/com/airwallexfyi/sources/AirwallexHttpClient.kt
- **Verification:** Full ./gradlew.bat test passes.
- **Committed in:** eb0a644

---

**Total deviations:** 2 auto-fixed (1 missing critical, 1 blocking)
**Impact on plan:** Both fixes preserve the planned architecture and make the implementation executable in the current Spring Boot test context.

## Issues Encountered

- Initial focused test run failed on an AssertJ/Kotlin lambda inference issue; simplified the assertion and reran successfully.
- Full test run then exposed the missing RestClient.Builder bean; fixed by making the component self-contained.

## Verification

- .\gradlew.bat test --tests "*AppPropertiesTest" --tests "*AirwallexSourceDiscoveryServiceTest" - passed.
- .\gradlew.bat test - passed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Wave 2 can use SitemapEntry, SourceType, and AirwallexHttpClient to fetch and extract article pages from discovered public candidates.

---
*Phase: AWXFYI-02-airwallex-source-monitoring*
*Completed: 2026-06-20*
