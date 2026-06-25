package com.airwallexfyi.summaries

import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.SourceType
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import tools.jackson.databind.ObjectMapper

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:summary_repository_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=true",
    ],
)
class SummaryRepositoryTest @Autowired constructor(
    private val postRepository: PostRepository,
    private val summaryRepository: SummaryRepository,
    private val objectMapper: ObjectMapper,
) {
    @Test
    fun `validated summary rejects malformed summary fields`() {
        assertThatThrownBy {
            StructuredSummary.validated(
                headline = " ",
                bullets = listOf("one", "two", "three"),
                whyItMatters = "Useful context",
                tags = listOf("payments"),
                sourceType = SourceType.BLOG,
            )
        }.isInstanceOf(SummaryValidationException::class.java)

        assertThatThrownBy {
            StructuredSummary.validated(
                headline = "A headline",
                bullets = listOf("one", "two"),
                whyItMatters = "Useful context",
                tags = listOf("payments"),
                sourceType = SourceType.BLOG,
            )
        }.isInstanceOf(SummaryValidationException::class.java)

        assertThatThrownBy {
            StructuredSummary.validated(
                headline = "A headline",
                bullets = listOf("one", "two", "three"),
                whyItMatters = " ",
                tags = listOf("payments"),
                sourceType = SourceType.BLOG,
            )
        }.isInstanceOf(SummaryValidationException::class.java)

        assertThatThrownBy {
            StructuredSummary.validated(
                headline = "A headline",
                bullets = listOf("one", "two", "three"),
                whyItMatters = "Useful context",
                tags = emptyList(),
                sourceType = SourceType.BLOG,
            )
        }.isInstanceOf(SummaryValidationException::class.java)
    }

    @Test
    fun `saves and finds summary records by post id`() {
        val post = postRepository.save(post())
        val summary = StructuredSummary.validated(
            headline = "Airwallex launches useful update",
            bullets = listOf(
                "Airwallex introduced a platform update.",
                "The change helps finance teams move faster.",
                "Customers can use the direct source link for details.",
            ),
            whyItMatters = "It signals a product direction worth tracking.",
            tags = listOf("product", "platform"),
            sourceType = SourceType.BLOG,
        )

        val saved = summaryRepository.save(
            SummaryRecord.from(
                postId = post.identifier(),
                summary = summary,
                model = "gemini-2.5-flash",
                promptVersion = "gemini-summary-v1",
                objectMapper = objectMapper,
                now = Instant.parse("2026-06-21T00:00:00Z"),
            ),
        )

        val found = summaryRepository.findByPostId(post.identifier())

        assertThat(found).isNotNull
        assertThat(found?.identifier()).isEqualTo(saved.identifier())
        assertThat(found?.headline).isEqualTo("Airwallex launches useful update")
        assertThat(found?.summaryJson).contains("why_it_matters")
        assertThat(found?.summaryJson).contains("Airwallex introduced a platform update")
        assertThat(found?.tagsJson).isEqualTo("[\"product\",\"platform\"]")
        assertThat(found?.toStructuredSummary(objectMapper)).isEqualTo(summary)
    }

    @Test
    fun `rejects duplicate summary for the same post`() {
        val post = postRepository.save(post(url = "https://www.airwallex.com/global/blog/summary-duplicate-${System.nanoTime()}"))
        val summary = StructuredSummary.validated(
            headline = "Duplicate summary",
            bullets = listOf("One", "Two", "Three"),
            whyItMatters = "It should only be stored once.",
            tags = listOf("test"),
            sourceType = SourceType.BLOG,
        )
        val record = SummaryRecord.from(post.identifier(), summary, "gemini-2.5-flash", "gemini-summary-v1", objectMapper)

        summaryRepository.save(record)

        assertThatThrownBy {
            summaryRepository.save(SummaryRecord.from(post.identifier(), summary, "gemini-2.5-flash", "gemini-summary-v1", objectMapper))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun post(url: String = "https://www.airwallex.com/global/blog/summary-${System.nanoTime()}"): PostRecord = PostRecord(
        url = url,
        sourceType = SourceType.BLOG.name,
        title = "Summary Source",
        description = "Article to summarize",
        author = "Airwallex",
        publishedAt = Instant.parse("2026-06-20T00:00:00Z"),
        sitemapLastmod = Instant.parse("2026-06-20T00:01:00Z"),
        discoveredAt = Instant.parse("2026-06-20T00:02:00Z"),
        contentHash = "sha256:summary",
        articleBody = "Long enough article body",
        processingStatus = "DISCOVERED",
        createdAt = Instant.parse("2026-06-20T00:03:00Z"),
        updatedAt = Instant.parse("2026-06-20T00:04:00Z"),
    )
}
