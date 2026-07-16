package com.airwallexfyi.spotlights

import com.airwallexfyi.articles.ArticleExtractor
import com.airwallexfyi.articles.ContentHashService
import com.airwallexfyi.articles.ExtractedArticle
import com.airwallexfyi.articles.RichTextFlattener
import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.digests.DigestEligibilityService
import com.airwallexfyi.monitor.PostStateService
import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.posts.SourceType
import com.airwallexfyi.sources.AirwallexHttpClient
import com.airwallexfyi.summaries.AiSummaryClient
import com.airwallexfyi.summaries.ArticleSummaryService
import com.airwallexfyi.summaries.StructuredSummary
import com.airwallexfyi.summaries.SummaryGenerationException
import com.airwallexfyi.summaries.SummaryRecord
import com.airwallexfyi.summaries.SummaryRepository
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
        "spring.datasource.url=jdbc:h2:mem:spotlight_service_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=true",
    ],
)
class SpotlightServiceTest @Autowired constructor(
    private val postRepository: PostRepository,
    private val summaryRepository: SummaryRepository,
    private val objectMapper: ObjectMapper,
    private val properties: AppProperties,
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    private val fakeHttpClient = FakeAirwallexHttpClient()
    private val fakeAiClient = FakeAiSummaryClient()
    private lateinit var service: SpotlightService

    @BeforeEach
    fun setUp() {
        summaryRepository.deleteAll()
        postRepository.deleteAll()
        fakeHttpClient.reset()
        fakeAiClient.reset()

        val postStateService = PostStateService(postRepository, jdbcTemplate, properties)
        val articleSummaryService = ArticleSummaryService(
            aiSummaryClient = fakeAiClient,
            summaryRepository = summaryRepository,
            objectMapper = objectMapper,
            properties = properties,
            jdbcTemplate = jdbcTemplate,
        )
        service = SpotlightService(
            postRepository = postRepository,
            summaryRepository = summaryRepository,
            articleExtractor = ArticleExtractor(fakeHttpClient, RichTextFlattener(), ContentHashService()),
            articleSummaryService = articleSummaryService,
            postStateService = postStateService,
            objectMapper = objectMapper,
        )
    }

    @Test
    fun `reuses an existing current summary without fetching or calling AI`() {
        val post = postRepository.save(post(status = ProcessingStatus.SUMMARY_READY, body = "Stored article body with enough detail for a useful summary."))
        summaryRepository.save(summaryRecord(post, summary(headline = "Stored Airwallex summary")))

        val message = service.formatSpotlight()

        assertThat(message).contains("[Blog] Stored Airwallex summary")
        assertThat(message).contains("Read: ${post.url}")
        assertThat(fakeHttpClient.requestedUrls).isEmpty()
        assertThat(fakeAiClient.calls).isZero()
    }

    @Test
    fun `generates a missing historical summary without making it digest eligible`() {
        val post = postRepository.save(post(status = ProcessingStatus.BASELINED, body = "Stored historical article body with enough detail for summarization."))

        val message = service.formatSpotlight()

        assertThat(message).contains("Generated Airwallex summary")
        assertThat(fakeAiClient.calls).isEqualTo(1)
        assertThat(summaryRepository.findByPostId(post.identifier())).isNotNull
        assertThat(postRepository.findById(post.identifier()).orElseThrow().processingStatus)
            .isEqualTo(ProcessingStatus.BASELINED.name)
        assertThat(DigestEligibilityService(summaryRepository, postRepository).findEligibleSummariesSince(null)).isEmpty()
    }

    @Test
    fun `hydrates only the selected post before generating its summary`() {
        val post = postRepository.save(post(status = ProcessingStatus.BASELINED, body = null, title = null))
        fakeHttpClient.response = """
            <html>
              <head><meta property="og:title" content="Hydrated Airwallex article"></head>
              <body><article>Airwallex published a detailed platform update with enough meaningful content for extraction.</article></body>
            </html>
        """.trimIndent()

        val message = service.formatSpotlight()

        val hydrated = postRepository.findById(post.identifier()).orElseThrow()
        assertThat(fakeHttpClient.requestedUrls).containsExactly(post.url)
        assertThat(hydrated.title).isEqualTo("Hydrated Airwallex article")
        assertThat(hydrated.articleBody).contains("detailed platform update")
        assertThat(hydrated.processingStatus).isEqualTo(ProcessingStatus.BASELINED.name)
        assertThat(summaryRepository.findByPostId(post.identifier())).isNotNull
        assertThat(message).contains("Read: ${post.url}")
    }

    @Test
    fun `replaces a stale summary after an explicit spotlight request`() {
        val post = postRepository.save(post(status = ProcessingStatus.APPROVAL_NEEDED, body = "Changed article body with enough information for a replacement summary."))
        summaryRepository.save(summaryRecord(post, summary(headline = "Old summary")))
        fakeAiClient.nextSummary = summary(headline = "Updated summary")

        val message = service.formatSpotlight()

        assertThat(message).contains("Updated summary")
        assertThat(summaryRepository.findByPostId(post.identifier())?.headline).isEqualTo("Updated summary")
        assertThat(postRepository.findById(post.identifier()).orElseThrow().processingStatus)
            .isEqualTo(ProcessingStatus.SUMMARY_READY.name)
    }

    @Test
    fun `returns the direct link when AI summarization fails`() {
        val post = postRepository.save(post(status = ProcessingStatus.BASELINED, body = "Stored article body with enough detail for an attempted summary."))
        fakeAiClient.failure = SummaryGenerationException("provider unavailable")

        val message = service.formatSpotlight()

        assertThat(message).contains("couldn't summarize")
        assertThat(message).contains(post.url)
        assertThat(summaryRepository.findByPostId(post.identifier())).isNull()
    }

    @Test
    fun `keeps spotlight response under the requested message cap`() {
        val post = postRepository.save(post(status = ProcessingStatus.SUMMARY_READY, body = "Stored article body with enough detail for a useful summary."))
        summaryRepository.save(
            summaryRecord(
                post,
                summary(
                    headline = "A very long Airwallex spotlight headline " + "A".repeat(300),
                    bullets = listOf(
                        "First complete detail " + "B".repeat(500),
                        "Second complete detail " + "C".repeat(500),
                        "Third complete detail " + "D".repeat(500),
                    ),
                    whyItMatters = "This is a long business context section " + "E".repeat(500),
                ),
            ),
        )

        val message = service.formatSpotlight(maxBodyChars = 700)

        assertThat(message.length).isLessThanOrEqualTo(700)
        assertThat(message).contains("Airwallex FYI Spotlight")
        assertThat(message).contains("omitted to fit Telegram")
        assertThat(message).contains("Read: ${post.url}")
    }

    private fun post(
        status: ProcessingStatus,
        body: String?,
        title: String? = "Airwallex article",
    ): PostRecord = PostRecord(
        url = "https://www.airwallex.com/global/blog/spotlight-${System.nanoTime()}",
        sourceType = SourceType.BLOG.name,
        title = title,
        publishedAt = Instant.parse("2026-07-01T00:00:00Z"),
        discoveredAt = Instant.parse("2026-07-01T00:00:00Z"),
        contentHash = body?.let { "hash-${it.length}" },
        articleBody = body,
        processingStatus = status.name,
        createdAt = Instant.parse("2026-07-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-07-01T00:00:00Z"),
    )

    private fun summary(
        headline: String = "Generated Airwallex summary",
        bullets: List<String> = listOf("First complete detail", "Second complete detail", "Third complete detail"),
        whyItMatters: String = "It gives readers useful business and product context.",
    ): StructuredSummary = StructuredSummary.validated(
        headline = headline,
        bullets = bullets,
        whyItMatters = whyItMatters,
        tags = listOf("payments"),
        sourceType = SourceType.BLOG,
    )

    private fun summaryRecord(post: PostRecord, summary: StructuredSummary): SummaryRecord = SummaryRecord.from(
        postId = post.identifier(),
        summary = summary,
        model = "test-model",
        promptVersion = "test-prompt",
        objectMapper = objectMapper,
    )

    private inner class FakeAiSummaryClient : AiSummaryClient {
        var calls: Int = 0
        var nextSummary: StructuredSummary = summary()
        var failure: RuntimeException? = null

        override fun summarize(article: ExtractedArticle): StructuredSummary {
            calls += 1
            failure?.let { throw it }
            return nextSummary
        }

        fun reset() {
            calls = 0
            nextSummary = summary()
            failure = null
        }
    }

    private class FakeAirwallexHttpClient : AirwallexHttpClient {
        val requestedUrls = mutableListOf<String>()
        var response: String = ""

        override fun fetchText(url: String): String {
            requestedUrls += url
            return response
        }

        fun reset() {
            requestedUrls.clear()
            response = ""
        }
    }
}
