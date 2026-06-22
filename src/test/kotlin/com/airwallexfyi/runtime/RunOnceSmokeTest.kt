package com.airwallexfyi.runtime

import com.airwallexfyi.articles.ExtractedArticle
import com.airwallexfyi.digests.DigestDeliveryPostRepository
import com.airwallexfyi.digests.DigestDeliveryRepository
import com.airwallexfyi.notifications.NotificationResult
import com.airwallexfyi.notifications.NotificationStatus
import com.airwallexfyi.notifications.WhatsAppAlertPayload
import com.airwallexfyi.notifications.WhatsAppNotifier
import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.posts.SourceType
import com.airwallexfyi.sources.AirwallexHttpClient
import com.airwallexfyi.subscribers.SubscriberChannelRepository
import com.airwallexfyi.subscribers.SubscriberRepository
import com.airwallexfyi.summaries.AiSummaryClient
import com.airwallexfyi.summaries.StructuredSummary
import com.airwallexfyi.summaries.SummaryRepository
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@SpringBootTest(
    properties = [
        "airwallex-fyi.dry-run=true",
        "airwallex-fyi.source.sitemap-url=$SITEMAP_URL",
        "airwallex-fyi.whatsapp.to=whatsapp:+15550000002",
        "spring.datasource.url=jdbc:h2:mem:run_once_smoke_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    ],
)
class RunOnceSmokeTest @Autowired constructor(
    private val command: MonitorRunOnceCommand,
    private val postRepository: PostRepository,
    private val summaryRepository: SummaryRepository,
    private val subscriberRepository: SubscriberRepository,
    private val subscriberChannelRepository: SubscriberChannelRepository,
    private val digestDeliveryRepository: DigestDeliveryRepository,
    private val digestDeliveryPostRepository: DigestDeliveryPostRepository,
    private val fixtureHttpClient: FixtureAirwallexHttpClient,
    private val smokeAiSummaryClient: SmokeAiSummaryClient,
    private val trackingWhatsAppNotifier: TrackingWhatsAppNotifier,
) {
    @BeforeEach
    fun deleteRows() {
        digestDeliveryPostRepository.deleteAll()
        digestDeliveryRepository.deleteAll()
        summaryRepository.deleteAll()
        postRepository.deleteAll()
        subscriberChannelRepository.deleteAll()
        subscriberRepository.deleteAll()
        fixtureHttpClient.reset()
        smokeAiSummaryClient.reset()
        trackingWhatsAppNotifier.reset()
    }

    @Test
    fun `run once command completes against fixtures without live providers`() {
        postRepository.save(knownPost())

        val exitCode = command.execute()

        val posts = postRepository.findAll()
        val smokePosts = posts.filter { it.url in setOf(BLOG_URL, NEWSROOM_URL) }
        assertThat(exitCode).isEqualTo(DefaultMonitorRunOnceCommand.EXIT_SUCCESS)
        assertThat(fixtureHttpClient.requests).contains(SITEMAP_URL, BLOG_URL, NEWSROOM_URL)
        assertThat(smokePosts.map { it.url }).containsExactlyInAnyOrder(BLOG_URL, NEWSROOM_URL)
        assertThat(smokePosts.map { it.processingStatus }.toSet()).containsExactly(ProcessingStatus.SUMMARY_READY.name)
        assertThat(summaryRepository.count()).isEqualTo(2)
        assertThat(smokeAiSummaryClient.calls).isEqualTo(2)
        assertThat(trackingWhatsAppNotifier.calls).isEqualTo(1)
        assertThat(trackingWhatsAppNotifier.results).allSatisfy { result ->
            assertThat(result.status).isEqualTo(NotificationStatus.DRY_RUN)
            assertThat(result.twilioCalled).isFalse()
        }
        assertThat(trackingWhatsAppNotifier.payloads.single().body)
            .contains("Airwallex FYI -")
            .contains(BLOG_URL)
            .contains(NEWSROOM_URL)
    }

    @TestConfiguration(proxyBeanMethods = false)
    class SmokeConfig {
        @Bean
        @Primary
        fun fixtureAirwallexHttpClient(): FixtureAirwallexHttpClient = FixtureAirwallexHttpClient()

        @Bean
        @Primary
        fun smokeAiSummaryClient(): SmokeAiSummaryClient = SmokeAiSummaryClient()

        @Bean
        @Primary
        fun trackingWhatsAppNotifier(): TrackingWhatsAppNotifier = TrackingWhatsAppNotifier()
    }

    private fun knownPost(): PostRecord = PostRecord(
        url = "https://www.airwallex.com/global/blog/already-known-smoke-anchor",
        sourceType = SourceType.BLOG.name,
        title = "Already known smoke anchor",
        description = "Existing record that makes fixture articles new work.",
        author = "Airwallex",
        publishedAt = Instant.parse("2026-06-16T00:00:00Z"),
        sitemapLastmod = Instant.parse("2026-06-16T00:00:00Z"),
        discoveredAt = Instant.parse("2026-06-16T00:00:00Z"),
        contentHash = "smoke-known-hash",
        articleBody = "Known article body that is long enough to count as stored content.",
        processingStatus = ProcessingStatus.ALERT_SENT.name,
    )
}

class FixtureAirwallexHttpClient : AirwallexHttpClient {
    val requests = mutableListOf<String>()

    override fun fetchText(url: String): String {
        requests += url
        return when (url) {
            SITEMAP_URL -> fixture("sitemap-blog.xml")
            BLOG_URL -> fixture("blog-agentos.html")
            NEWSROOM_URL -> fixture("newsroom-leapfin.html")
            else -> error("missing smoke fixture for $url")
        }
    }

    fun reset() {
        requests.clear()
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/airwallex/$name")) { "Missing fixture $name" }.readText()
}

class SmokeAiSummaryClient : AiSummaryClient {
    var calls = 0

    override fun summarize(article: ExtractedArticle): StructuredSummary {
        calls += 1
        return StructuredSummary.validated(
            headline = "Smoke summary for ${article.sourceType.name.lowercase()}",
            bullets = listOf(
                "Airwallex published a fixture-backed update",
                "The monitor extracted article content locally",
                "The dry-run digest can include this source link",
            ),
            whyItMatters = "It proves the run-once command can exercise the monitor path without live providers.",
            tags = listOf("smoke", "dry-run"),
            sourceType = article.sourceType,
        )
    }

    fun reset() {
        calls = 0
    }
}

class TrackingWhatsAppNotifier : WhatsAppNotifier {
    var calls = 0
    val payloads = mutableListOf<WhatsAppAlertPayload>()
    val results = mutableListOf<NotificationResult>()

    override fun send(payload: WhatsAppAlertPayload): NotificationResult {
        calls += 1
        payloads += payload
        return NotificationResult(
            status = NotificationStatus.DRY_RUN,
            payloadPreview = payload.preview,
            twilioCalled = false,
        ).also { results += it }
    }

    fun reset() {
        calls = 0
        payloads.clear()
        results.clear()
    }
}

private const val SITEMAP_URL = "https://www.airwallex.com/global/sitemap-blog.xml"
private const val BLOG_URL =
    "https://www.airwallex.com/global/blog/introducing-airwallex-agentos-manage-your-financial-operations-in-your-preferred-agent-environment"
private const val NEWSROOM_URL =
    "https://www.airwallex.com/global/newsroom/airwallex-acquires-leapfin-expanding-financial-lifecycle-capabilities"
