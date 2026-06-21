package com.airwallexfyi.monitor

import com.airwallexfyi.articles.ArticleExtractor
import com.airwallexfyi.articles.ContentHashService
import com.airwallexfyi.articles.ExtractedArticle
import com.airwallexfyi.articles.RichTextFlattener
import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.notifications.NotificationResult
import com.airwallexfyi.notifications.NotificationStatus
import com.airwallexfyi.notifications.WhatsAppAlertFormatter
import com.airwallexfyi.notifications.WhatsAppAlertPayload
import com.airwallexfyi.notifications.WhatsAppNotifier
import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.posts.SourceType
import com.airwallexfyi.sources.AirwallexHttpClient
import com.airwallexfyi.sources.AirwallexSourceDiscoveryService
import com.airwallexfyi.summaries.AiSummaryClient
import com.airwallexfyi.summaries.ArticleSummaryService
import com.airwallexfyi.summaries.SummaryGenerationException
import com.airwallexfyi.summaries.SummaryRecord
import com.airwallexfyi.summaries.SummaryRepository
import com.airwallexfyi.summaries.StructuredSummary
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import tools.jackson.databind.ObjectMapper

@SpringBootTest(
    properties = [
        "airwallex-fyi.source.first-run-seed-limit=25",
        "spring.datasource.url=jdbc:h2:mem:monitor_run_service_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    ],
)
class MonitorRunServiceTest @Autowired constructor(
    private val postRepository: PostRepository,
    private val summaryRepository: SummaryRepository,
    private val postStateService: PostStateService,
    private val objectMapper: ObjectMapper,
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    @BeforeEach
    fun deleteRows() {
        summaryRepository.deleteAll()
        postRepository.deleteAll()
    }

    @Test
    fun `run once seeds discovered articles on first run without ai or notifier`() {
        val urls = listOf(blogUrl("seed-one"), blogUrl("seed-two"))
        val aiClient = FakeAiSummaryClient(summary())
        val notifier = FakeWhatsAppNotifier(NotificationStatus.DRY_RUN)
        val service = monitorService(
            sitemapXml = sitemap(urls),
            articleBodies = urls.associateWith { fixture("/fixtures/airwallex/blog-agentos.html") },
            aiClient = aiClient,
            notifier = notifier,
        )

        val result = service.runOnce()

        assertThat(result.status).isEqualTo(MonitorRunStatus.COMPLETED)
        assertThat(result.sitemapFetched).isTrue()
        assertThat(result.discoveredCount).isEqualTo(2)
        assertThat(result.seededCount).isEqualTo(2)
        assertThat(result.newCount).isZero()
        assertThat(result.updatedCount).isZero()
        assertThat(result.failedCount).isZero()
        assertThat(result.summarizedCount).isZero()
        assertThat(result.approvalNeededCount).isEqualTo(2)
        assertThat(result.sampleApprovalNeeded).allSatisfy { item -> assertThat(item.reason).isEqualTo("missing_summary") }
        assertThat(result.externalCallsTriggered).isFalse()
        assertThat(result.twilioCallsTriggered).isFalse()
        assertThat(result.sampleUrls.seeded).containsExactlyElementsOf(urls.asReversed())
        assertThat(aiClient.calls).isZero()
        assertThat(notifier.calls).isZero()
        assertThat(postRepository.count()).isEqualTo(2)
        assertThat(postRepository.findAll().map { it.processingStatus }.toSet()).containsExactly(ProcessingStatus.APPROVAL_NEEDED.name)
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
        assertThat(second.approvalNeededCount).isEqualTo(2)
        assertThat(postRepository.count()).isEqualTo(2)
    }

    @Test
    fun `second run with a new url summarizes and dry run notifies`() {
        saveKnownPost(blogUrl("already-known"))
        val newUrl = blogUrl("fresh-update")
        val aiClient = FakeAiSummaryClient(summary())
        val notifier = FakeWhatsAppNotifier(NotificationStatus.DRY_RUN)
        val service = monitorService(
            sitemapXml = sitemap(listOf(newUrl)),
            articleBodies = mapOf(newUrl to fixture("/fixtures/airwallex/blog-agentos.html")),
            aiClient = aiClient,
            notifier = notifier,
        )

        val result = service.runOnce()

        assertThat(result.status).isEqualTo(MonitorRunStatus.COMPLETED)
        assertThat(result.newCount).isEqualTo(1)
        assertThat(result.summarizedCount).isEqualTo(1)
        assertThat(result.dryRunAlertCount).isEqualTo(1)
        assertThat(result.alertSentCount).isZero()
        assertThat(result.summaryFailedCount).isZero()
        assertThat(result.samplePayloads.single()).contains("Airwallex FYI: Airwallex updates its platform")
        assertThat(result.externalCallsTriggered).isTrue()
        assertThat(result.twilioCallsTriggered).isFalse()
        assertThat(aiClient.calls).isEqualTo(1)
        assertThat(notifier.calls).isEqualTo(1)
        val post = requireNotNull(postRepository.findByUrl(newUrl))
        assertThat(post.processingStatus).isEqualTo(ProcessingStatus.DRY_RUN_READY.name)
        assertThat(summaryRepository.findByPostId(post.identifier())).isNotNull
    }

    @Test
    fun `live notifier success records alert sent and twilio call flag`() {
        saveKnownPost(blogUrl("known-before-live"))
        val newUrl = blogUrl("live-update")
        val notifier = FakeWhatsAppNotifier(NotificationStatus.SENT, twilioCalled = true)
        val service = monitorService(
            sitemapXml = sitemap(listOf(newUrl)),
            articleBodies = mapOf(newUrl to fixture("/fixtures/airwallex/blog-agentos.html")),
            notifier = notifier,
        )

        val result = service.runOnce()

        assertThat(result.alertSentCount).isEqualTo(1)
        assertThat(result.dryRunAlertCount).isZero()
        assertThat(result.twilioCallsTriggered).isTrue()
        assertThat(postRepository.findByUrl(newUrl)?.processingStatus).isEqualTo(ProcessingStatus.ALERT_SENT.name)
    }

    @Test
    fun `summary failure blocks notifier and marks summary failed`() {
        saveKnownPost(blogUrl("known-before-failure"))
        val newUrl = blogUrl("summary-failure")
        val aiClient = FakeAiSummaryClient(failure = SummaryGenerationException("invalid json"))
        val notifier = FakeWhatsAppNotifier(NotificationStatus.DRY_RUN)
        val service = monitorService(
            sitemapXml = sitemap(listOf(newUrl)),
            articleBodies = mapOf(newUrl to fixture("/fixtures/airwallex/blog-agentos.html")),
            aiClient = aiClient,
            notifier = notifier,
        )

        val result = service.runOnce()

        assertThat(result.status).isEqualTo(MonitorRunStatus.PARTIAL_FAILURE)
        assertThat(result.summaryFailedCount).isEqualTo(1)
        assertThat(result.dryRunAlertCount).isZero()
        assertThat(result.sampleErrors.single().reason).contains("Summary failed: invalid json")
        assertThat(notifier.calls).isZero()
        val post = requireNotNull(postRepository.findByUrl(newUrl))
        assertThat(post.processingStatus).isEqualTo(ProcessingStatus.SUMMARY_FAILED.name)
        assertThat(summaryRepository.findByPostId(post.identifier())).isNull()
    }

    @Test
    fun `notifier failure marks alert failed without retry`() {
        saveKnownPost(blogUrl("known-before-alert-failure"))
        val newUrl = blogUrl("alert-failure")
        val notifier = FakeWhatsAppNotifier(NotificationStatus.FAILED, errorMessage = "twilio down", twilioCalled = true)
        val service = monitorService(
            sitemapXml = sitemap(listOf(newUrl)),
            articleBodies = mapOf(newUrl to fixture("/fixtures/airwallex/blog-agentos.html")),
            notifier = notifier,
        )

        val result = service.runOnce()

        assertThat(result.status).isEqualTo(MonitorRunStatus.PARTIAL_FAILURE)
        assertThat(result.summarizedCount).isEqualTo(1)
        assertThat(result.alertFailedCount).isEqualTo(1)
        assertThat(result.sampleErrors.single().reason).contains("Alert failed: twilio down")
        assertThat(result.twilioCallsTriggered).isTrue()
        assertThat(notifier.calls).isEqualTo(1)
        assertThat(postRepository.findByUrl(newUrl)?.processingStatus).isEqualTo(ProcessingStatus.ALERT_FAILED.name)
    }

    @Test
    fun `known url content hash change becomes approval needed without duplicate alert`() {
        val url = blogUrl("changed-known")
        val existing = saveKnownPost(url, contentHash = "old-hash", sitemapLastmod = Instant.parse("2026-06-20T00:00:00Z"))
        summaryRepository.save(SummaryRecord.from(existing.identifier(), summary(), "gemini-test", "gemini-summary-v1", objectMapper))
        val aiClient = FakeAiSummaryClient(summary())
        val notifier = FakeWhatsAppNotifier(NotificationStatus.DRY_RUN)
        val service = monitorService(
            sitemapXml = sitemap(listOf(url), lastmod = Instant.parse("2026-06-21T00:00:00Z")),
            articleBodies = mapOf(url to fixture("/fixtures/airwallex/blog-agentos.html")),
            aiClient = aiClient,
            notifier = notifier,
        )

        val result = service.runOnce()

        assertThat(result.updatedCount).isEqualTo(1)
        assertThat(result.approvalNeededCount).isEqualTo(1)
        assertThat(result.sampleApprovalNeeded.single().reason).isEqualTo("content_changed")
        assertThat(result.summarizedCount).isZero()
        assertThat(result.dryRunAlertCount).isZero()
        assertThat(aiClient.calls).isZero()
        assertThat(notifier.calls).isZero()
        assertThat(postRepository.findByUrl(url)?.processingStatus).isEqualTo(ProcessingStatus.APPROVAL_NEEDED.name)
        assertThat(summaryRepository.count()).isEqualTo(1)
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
        assertThat(result.approvalNeededCount).isEqualTo(1)
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
            postRepository = postRepository,
            summaryRepository = summaryRepository,
            articleSummaryService = summaryService(FakeAiSummaryClient(summary())),
            alertFormatter = WhatsAppAlertFormatter(),
            whatsAppNotifier = FakeWhatsAppNotifier(NotificationStatus.DRY_RUN),
            properties = testProperties(),
        )

        val result = service.runOnce()

        assertThat(result.status).isEqualTo(MonitorRunStatus.FAILED)
        assertThat(result.sitemapFetched).isFalse()
        assertThat(result.failedCount).isEqualTo(1)
        assertThat(result.summarizedCount).isZero()
        assertThat(result.twilioCallsTriggered).isFalse()
        val error = result.sampleErrors.single()
        assertThat(error.url).isNull()
        assertThat(error.reason).contains("sitemap down")
        assertThat(postRepository.count()).isZero()
    }

    private fun monitorService(
        sitemapXml: String,
        articleBodies: Map<String, String>,
        aiClient: FakeAiSummaryClient = FakeAiSummaryClient(summary()),
        notifier: FakeWhatsAppNotifier = FakeWhatsAppNotifier(NotificationStatus.DRY_RUN),
    ): MonitorRunService = MonitorRunService(
        sourceDiscoveryService = AirwallexSourceDiscoveryService(testProperties(), StaticHttpClient(sitemapXml)),
        articleExtractor = articleExtractor(articleBodies),
        postStateService = postStateService,
        postRepository = postRepository,
        summaryRepository = summaryRepository,
        articleSummaryService = summaryService(aiClient),
        alertFormatter = WhatsAppAlertFormatter(),
        whatsAppNotifier = notifier,
        properties = testProperties(),
    )

    private fun summaryService(aiClient: AiSummaryClient): ArticleSummaryService = ArticleSummaryService(
        aiSummaryClient = aiClient,
        summaryRepository = summaryRepository,
        objectMapper = objectMapper,
        properties = AppProperties(ai = AppProperties.Ai(model = "gemini-test")),
        jdbcTemplate = jdbcTemplate,
    )

    private fun articleExtractor(articleBodies: Map<String, String>): ArticleExtractor = ArticleExtractor(
        httpClient = MapHttpClient(articleBodies),
        richTextFlattener = RichTextFlattener(),
        contentHashService = ContentHashService(),
    )

    private fun saveKnownPost(
        url: String,
        contentHash: String = "known-hash",
        sitemapLastmod: Instant = Instant.parse("2026-06-20T00:00:00Z"),
    ): PostRecord = postRepository.save(
        PostRecord(
            url = url,
            sourceType = SourceType.BLOG.name,
            title = "Known post",
            description = "Already known",
            author = "Airwallex",
            publishedAt = sitemapLastmod,
            sitemapLastmod = sitemapLastmod,
            discoveredAt = sitemapLastmod,
            contentHash = contentHash,
            articleBody = "Known article body that is long enough for a stored post.",
            processingStatus = ProcessingStatus.ALERT_SENT.name,
        ),
    )

    private fun sitemap(urls: List<String>, lastmod: Instant = Instant.parse("2026-06-20T00:00:00Z")): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<urlset>")
        urls.forEachIndexed { index, url ->
            appendLine("  <url>")
            appendLine("    <loc>$url</loc>")
            appendLine("    <lastmod>${lastmod.plusSeconds(index.toLong())}</lastmod>")
            appendLine("  </url>")
        }
        appendLine("</urlset>")
    }

    private fun summary(): StructuredSummary = StructuredSummary.validated(
        headline = "Airwallex updates its platform",
        bullets = listOf(
            "New payment tooling shipped",
            "Finance teams get faster workflows",
            "The update links back to the source",
        ),
        whyItMatters = "It points to Airwallex investing in operational finance workflows.",
        tags = listOf("payments", "platform"),
        sourceType = SourceType.BLOG,
    )

    private fun testProperties(): AppProperties = AppProperties(
        whatsapp = AppProperties.WhatsApp(to = "whatsapp:+15550000002"),
    )

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

    private class FakeAiSummaryClient(
        private val summary: StructuredSummary? = null,
        private val failure: RuntimeException? = null,
    ) : AiSummaryClient {
        var calls = 0

        override fun summarize(article: ExtractedArticle): StructuredSummary {
            calls += 1
            failure?.let { throw it }
            return requireNotNull(summary)
        }
    }

    private class FakeWhatsAppNotifier(
        private val status: NotificationStatus,
        private val errorMessage: String? = null,
        private val twilioCalled: Boolean = false,
    ) : WhatsAppNotifier {
        var calls = 0
        val payloads = mutableListOf<WhatsAppAlertPayload>()

        override fun send(post: PostRecord, payload: WhatsAppAlertPayload): NotificationResult {
            calls += 1
            payloads += payload
            return NotificationResult(
                status = status,
                payloadPreview = payload.preview,
                providerMessageId = if (status == NotificationStatus.SENT) "SM123" else null,
                errorMessage = errorMessage,
                twilioCalled = twilioCalled,
            )
        }
    }
}