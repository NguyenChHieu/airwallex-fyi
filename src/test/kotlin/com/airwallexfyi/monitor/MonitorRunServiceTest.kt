package com.airwallexfyi.monitor

import com.airwallexfyi.articles.ArticleExtractor
import com.airwallexfyi.articles.ContentHashService
import com.airwallexfyi.articles.RichTextFlattener
import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.sources.AirwallexHttpClient
import com.airwallexfyi.sources.AirwallexSourceDiscoveryService
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    properties = [
        "airwallex-fyi.source.first-run-seed-limit=25",
        "spring.datasource.url=jdbc:h2:mem:monitor_run_service_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    ],
)
class MonitorRunServiceTest @Autowired constructor(
    private val postRepository: PostRepository,
    private val postStateService: PostStateService,
) {
    @BeforeEach
    fun deleteRows() {
        postRepository.deleteAll()
    }

    @Test
    fun `run once seeds discovered articles on first run`() {
        val urls = listOf(blogUrl("seed-one"), blogUrl("seed-two"))
        val service = monitorService(
            sitemapXml = sitemap(urls),
            articleBodies = urls.associateWith { fixture("/fixtures/airwallex/blog-agentos.html") },
        )

        val result = service.runOnce()

        assertThat(result.status).isEqualTo(MonitorRunStatus.COMPLETED)
        assertThat(result.sitemapFetched).isTrue()
        assertThat(result.discoveredCount).isEqualTo(2)
        assertThat(result.seededCount).isEqualTo(2)
        assertThat(result.newCount).isZero()
        assertThat(result.updatedCount).isZero()
        assertThat(result.failedCount).isZero()
        assertThat(result.externalCallsTriggered).isFalse()
        assertThat(result.sampleUrls.seeded).containsExactlyElementsOf(urls.asReversed())
        assertThat(postRepository.count()).isEqualTo(2)
    }

    @Test
    fun `run once skips known unchanged urls on repeat run`() {
        val urls = listOf(blogUrl("repeat-one"), blogUrl("repeat-two"))
        val service = monitorService(
            sitemapXml = sitemap(urls),
            articleBodies = urls.associateWith { fixture("/fixtures/airwallex/blog-agentos.html") },
        )
        service.runOnce()

        val second = service.runOnce()

        assertThat(second.status).isEqualTo(MonitorRunStatus.COMPLETED)
        assertThat(second.seededCount).isZero()
        assertThat(second.newCount).isZero()
        assertThat(second.updatedCount).isZero()
        assertThat(second.skippedCount).isEqualTo(2)
        assertThat(postRepository.count()).isEqualTo(2)
    }

    @Test
    fun `per article extraction failure is sampled and does not stop the run`() {
        val good = blogUrl("good")
        val bad = blogUrl("bad")
        val service = monitorService(
            sitemapXml = sitemap(listOf(good, bad)),
            articleBodies = mapOf(good to fixture("/fixtures/airwallex/blog-agentos.html")),
        )

        val result = service.runOnce()

        assertThat(result.status).isEqualTo(MonitorRunStatus.PARTIAL_FAILURE)
        assertThat(result.seededCount).isEqualTo(1)
        assertThat(result.failedCount).isEqualTo(1)
        val error = result.sampleErrors.single()
        assertThat(error.url).isEqualTo(bad)
        assertThat(error.reason).contains("missing fixture")
        assertThat(postRepository.count()).isEqualTo(1)
    }

    @Test
    fun `sitemap discovery failure writes no posts and reports failed status`() {
        val service = MonitorRunService(
            sourceDiscoveryService = AirwallexSourceDiscoveryService(AppProperties(), ThrowingHttpClient(IllegalStateException("sitemap down"))),
            articleExtractor = articleExtractor(emptyMap()),
            postStateService = postStateService,
        )

        val result = service.runOnce()

        assertThat(result.status).isEqualTo(MonitorRunStatus.FAILED)
        assertThat(result.sitemapFetched).isFalse()
        assertThat(result.failedCount).isEqualTo(1)
        val error = result.sampleErrors.single()
        assertThat(error.url).isNull()
        assertThat(error.reason).contains("sitemap down")
        assertThat(postRepository.count()).isZero()
    }

    private fun monitorService(sitemapXml: String, articleBodies: Map<String, String>): MonitorRunService = MonitorRunService(
        sourceDiscoveryService = AirwallexSourceDiscoveryService(AppProperties(), StaticHttpClient(sitemapXml)),
        articleExtractor = articleExtractor(articleBodies),
        postStateService = postStateService,
    )

    private fun articleExtractor(articleBodies: Map<String, String>): ArticleExtractor = ArticleExtractor(
        httpClient = MapHttpClient(articleBodies),
        richTextFlattener = RichTextFlattener(),
        contentHashService = ContentHashService(),
    )

    private fun sitemap(urls: List<String>): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<urlset>")
        urls.forEachIndexed { index, url ->
            appendLine("  <url>")
            appendLine("    <loc>$url</loc>")
            appendLine("    <lastmod>${Instant.parse("2026-06-20T00:00:00Z").plusSeconds(index.toLong())}</lastmod>")
            appendLine("  </url>")
        }
        appendLine("</urlset>")
    }

    private fun blogUrl(slug: String): String = "https://www.airwallex.com/global/blog/$slug"

    private fun fixture(path: String): String =
        requireNotNull(javaClass.getResource(path)) { "Missing fixture $path" }.readText()

    private class StaticHttpClient(private val body: String) : AirwallexHttpClient {
        override fun fetchText(url: String): String = body
    }

    private class MapHttpClient(private val bodies: Map<String, String>) : AirwallexHttpClient {
        override fun fetchText(url: String): String =
            bodies[url] ?: throw IllegalStateException("missing fixture for $url")
    }

    private class ThrowingHttpClient(private val failure: RuntimeException) : AirwallexHttpClient {
        override fun fetchText(url: String): String = throw failure
    }
}