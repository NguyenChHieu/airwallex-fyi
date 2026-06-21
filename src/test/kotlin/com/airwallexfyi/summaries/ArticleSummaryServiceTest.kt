package com.airwallexfyi.summaries

import com.airwallexfyi.articles.ExtractedArticle
import com.airwallexfyi.articles.ExtractionSource
import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.SourceType
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.ObjectMapper

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:article_summary_service_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    ],
)
class ArticleSummaryServiceTest @Autowired constructor(
    private val postRepository: PostRepository,
    private val summaryRepository: SummaryRepository,
    private val objectMapper: ObjectMapper,
) {
    @BeforeEach
    fun deleteRows() {
        summaryRepository.deleteAll()
        postRepository.deleteAll()
    }

    @Test
    fun `successful summarization persists one summary row`() {
        val fakeClient = FakeAiSummaryClient(summary = summary())
        val service = service(fakeClient)
        val post = postRepository.save(post())

        val result = service.summarize(post, article())

        assertThat(result).isInstanceOf(ArticleSummaryResult.Success::class.java)
        val saved = summaryRepository.findByPostId(post.identifier())
        assertThat(saved).isNotNull
        assertThat(saved?.headline).isEqualTo("Airwallex updates its platform")
        assertThat(saved?.model).isEqualTo("gemini-test")
        assertThat(saved?.promptVersion).isEqualTo(ArticleSummaryService.PROMPT_VERSION)
        assertThat(saved?.toStructuredSummary(objectMapper)).isEqualTo(summary())
        assertThat(fakeClient.calls).isEqualTo(1)
    }

    @Test
    fun `failed summarization returns bounded failure and creates no row`() {
        val fakeClient = FakeAiSummaryClient(failure = SummaryGenerationException("bad json\nwith details that should be bounded"))
        val service = service(fakeClient)
        val post = postRepository.save(post(url = "https://www.airwallex.com/global/blog/summary-fails-${System.nanoTime()}"))

        val result = service.summarize(post, article(url = post.url))

        assertThat(result).isEqualTo(ArticleSummaryResult.Failure(reason = "bad json"))
        assertThat(summaryRepository.findByPostId(post.identifier())).isNull()
        assertThat(fakeClient.calls).isEqualTo(1)
    }

    private fun service(fakeClient: FakeAiSummaryClient): ArticleSummaryService = ArticleSummaryService(
        aiSummaryClient = fakeClient,
        summaryRepository = summaryRepository,
        objectMapper = objectMapper,
        properties = AppProperties(ai = AppProperties.Ai(model = "gemini-test")),
    )

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

    private fun article(url: String = "https://www.airwallex.com/global/blog/summary-success"): ExtractedArticle = ExtractedArticle(
        url = url,
        sourceType = SourceType.BLOG,
        title = "Airwallex test update",
        description = "A public update from Airwallex",
        author = "Airwallex",
        publishedAt = Instant.parse("2026-06-20T00:00:00Z"),
        bodyText = "Airwallex shared a long public update about product and platform changes. ".repeat(8),
        contentHash = "sha256:test",
        extractionSource = ExtractionSource.STRUCTURED,
    )

    private fun post(url: String = "https://www.airwallex.com/global/blog/summary-success"): PostRecord = PostRecord(
        url = url,
        sourceType = SourceType.BLOG.name,
        title = "Airwallex test update",
        description = "A public update from Airwallex",
        publishedAt = Instant.parse("2026-06-20T00:00:00Z"),
        contentHash = "sha256:test",
        articleBody = "Airwallex shared a long public update about product and platform changes.",
        processingStatus = "DISCOVERED",
    )

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
}
