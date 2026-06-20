---
phase: AWXFYI-02-airwallex-source-monitoring
created: 2026-06-20
status: ready-for-planning
sources_verified: true
---

# Phase 2 Research: Airwallex Source Monitoring

## Research Question

What does the planner need to know to implement Phase 2: discover, extract, and dedupe public Airwallex Blog and Newsroom articles without sending notifications?

## Verified Live Source Facts

Checked on 2026-06-20 from public Airwallex endpoints.

### Sitemap

- Source: `https://www.airwallex.com/global/sitemap-blog.xml`
- HTTP status: 200
- Current sitemap entries: 710
- Strict article candidates currently found:
  - Blog article URLs matching `/global/blog/{slug}`: 486
  - Newsroom article URLs matching `/global/newsroom/{slug}`: 189
  - Pagination URLs containing `/page/`: 27
- All currently accepted Blog and Newsroom article URLs had `<lastmod>` values during the check.
- Example pagination entries exist, such as `/global/blog/page/2`, so Phase 2 must explicitly exclude pagination paths.

### Robots And Source Policy

- Source: `https://www.airwallex.com/robots.txt`
- Default user agent is allowed broadly with private/app/API paths disallowed.
- Disallowed paths include `/api/fx/`, `/api/v1/`, `/app/`, `/app1/`, `/webapp/login/`, and related app/sdk paths.
- Phase 2 should fetch only public sitemap and article pages, never disallowed app/API paths.

### Blog Article Shape

Verified sample:
`https://www.airwallex.com/global/blog/introducing-airwallex-agentos-manage-your-financial-operations-in-your-preferred-agent-environment`

- Raw HTML contains `__NEXT_DATA__`.
- Main article object: `props.pageProps.pageQuery.pageData.post.fields`.
- Verified field keys include `title`, `slug`, `region`, `description`, `author`, `date`, `readTime`, `content`, and `category`.
- `content.nodeType` is `document` and the content tree contains Contentful-style rich text nodes such as headings, paragraphs, hyperlinks, and text nodes.

### Newsroom Article Shape

Verified sample:
`https://www.airwallex.com/global/newsroom/airwallex-acquires-leapfin-expanding-financial-lifecycle-capabilities`

- Raw HTML contains `__NEXT_DATA__`.
- Main article object: `props.pageProps.pageQuery.pageData.pr.fields`.
- Verified field keys include `title`, `slug`, `description`, `heading`, `date`, `content`, and `regions`.
- `content.nodeType` is `document` and the content tree uses the same rich-text shape.

## Technical Research

### HTTP Fetching

Use Spring Framework `RestClient` for synchronous HTTP requests. Spring Framework documents `RestClient` as the synchronous fluent REST client and notes that `RestTemplate` is deprecated in favor of `RestClient` as of Spring Framework 7.0. The app already uses Spring WebMVC, so `RestClient` fits the current blocking MVC/service style without introducing WebFlux.

Reference: https://docs.spring.io/spring-framework/reference/integration/rest-clients.html

Planning implication:
- Add an `AirwallexHttpClient` or similar wrapper around `RestClient` so tests can fake sitemap/article HTML without network access.
- Do not put direct network calls into parser or persistence services.

### XML And HTML Parsing

Add Jsoup for HTML parsing and fallback extraction. Maven Central currently reports `org.jsoup:jsoup` latest/release `1.22.2` with metadata last updated 2026-04-20.

Jsoup official cookbook supports parsing HTML strings into `Document` objects and using DOM-like methods to navigate and extract text/attributes. That makes it suitable for extracting the `#__NEXT_DATA__` script and for HTML fallback body extraction.

References:
- https://jsoup.org/cookbook/input/parse-document-from-string
- https://jsoup.org/cookbook/extracting-data/dom-navigation
- https://repo1.maven.org/maven2/org/jsoup/jsoup/maven-metadata.xml

Planning implication:
- Add `implementation("org.jsoup:jsoup:1.22.2")`.
- Use Jsoup to parse article HTML and select the `script#__NEXT_DATA__` payload.
- Use Jackson tree parsing for the JSON payload; avoid mapping the entire Airwallex payload to rigid data classes because the CMS object is large and may shift.
- Use a dedicated rich-text flattener that recursively collects text nodes and inserts readable spacing around headings/paragraphs/list items.

### Persistence And Dedupe

Existing Phase 1 persistence is enough for Phase 2:

- `posts.url` has a unique constraint.
- `PostRepository.findByUrl(url)` supports URL-first dedupe.
- `PostRecord` already includes URL, source type, title, description, author, published date, sitemap lastmod, discovered timestamp, content hash, article body, processing status, created/updated timestamps.

Planning implication:
- Do not add MongoDB or a broad schema rewrite.
- Do not add raw payload persistence in Phase 2.
- Use `processing_status` values to make baseline/new/fallback/updated behavior visible if this can be done without schema churn.
- If repository update operations are awkward with current `Persistable` new-record behavior, the plan should allow adding focused repository methods using `@Query`/`@Modifying` or another Spring Data JDBC-friendly update path.

### Tests And Fixtures

Do not make parser tests depend on live Airwallex network. Phase 2 should create small representative fixtures for:

- One Blog article containing `pageData.post.fields`.
- One Newsroom article containing `pageData.pr.fields`.
- One sitemap fixture containing accepted Blog/Newsroom URLs plus rejected pagination/non-article examples.
- One malformed/missing structured data fixture for HTML fallback.

Planning implication:
- Unit tests cover URL filtering, `__NEXT_DATA__` extraction, rich-text flattening, fallback extraction, hash normalization, and missing optional fields.
- Integration tests cover first-run seed size, URL-only dedupe, content update behavior, and run result counts with fake fetcher responses.

## Recommended Plan Shape

Split Phase 2 into four executable plans:

1. **Source discovery and fixtures** - add Jsoup, fakeable HTTP fetcher, sitemap parser, strict URL filter, fixtures/tests.
2. **Article extraction and hashing** - structured `__NEXT_DATA__` extractor for Blog/Newsroom, rich-text flattener, HTML fallback, content hash, fixtures/tests.
3. **Monitor run persistence and dedupe** - replace run-once stub with discovery/extraction/persist orchestration, first-run seed latest 25, URL dedupe, content updates, partial failure behavior.
4. **Admin visibility** - update `/admin/run-once` response DTOs and `/admin/posts/recent` filters/body previews, MockMvc tests.

## Risks And Mitigations

- **Airwallex page shape changes:** Keep verified fixtures and parser tests. Parser rules must be updated from evidence, not assumptions.
- **Sitemap includes broad non-article URLs:** Strict path regex and tests with rejected examples.
- **Historical spam later:** Seed baseline status/counts in Phase 2 so Phase 3 can distinguish baseline from new.
- **One bad article breaks run:** Continue per article and return `partial_failure` with sampled errors.
- **Network flakiness in tests:** HTTP fetcher abstraction plus local fixtures.
- **Over-expanding persistence:** Use existing Postgres schema; defer raw payload/media persistence.

## RESEARCH COMPLETE
