package com.airwallexfyi.digests

import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.posts.SourceType
import com.airwallexfyi.summaries.StructuredSummary
import com.airwallexfyi.summaries.SummaryRecord
import com.airwallexfyi.summaries.SummaryRepository
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.ObjectMapper

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:latest_updates_service_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=true",
    ],
)
class LatestUpdatesServiceTest @Autowired constructor(
    private val postRepository: PostRepository,
    private val summaryRepository: SummaryRepository,
    private val objectMapper: ObjectMapper,
) {
    private val service by lazy { LatestUpdatesService(summaryRepository, postRepository, objectMapper) }

    @BeforeEach
    fun clearData() {
        summaryRepository.deleteAll()
        postRepository.deleteAll()
    }

    @Test
    fun `latest orders by newest Airwallex post date before summary creation time`() {
        createSummarizedPost(
            slug = "older-but-recently-summarized",
            headline = "Older post summarized later",
            publishedAt = Instant.parse("2026-06-25T00:00:00Z"),
            summaryCreatedAt = Instant.parse("2026-07-08T00:00:00Z"),
        )
        createSummarizedPost(
            slug = "newer-but-summarized-earlier",
            headline = "Newer post summarized earlier",
            publishedAt = Instant.parse("2026-07-02T00:00:00Z"),
            summaryCreatedAt = Instant.parse("2026-07-07T00:00:00Z"),
        )

        val body = service.formatLatest()

        assertThat(body).containsSubsequence(
            "1. Newer post summarized earlier",
            "2. Older post summarized later",
        )
    }

    private fun createSummarizedPost(
        slug: String,
        headline: String,
        publishedAt: Instant,
        summaryCreatedAt: Instant,
    ) {
        val post = postRepository.save(
            PostRecord(
                url = "https://www.airwallex.com/global/blog/$slug",
                sourceType = SourceType.BLOG.name,
                title = headline,
                publishedAt = publishedAt,
                discoveredAt = publishedAt,
                processingStatus = ProcessingStatus.SUMMARY_READY.name,
                createdAt = publishedAt,
                updatedAt = publishedAt,
            ),
        )
        summaryRepository.save(
            SummaryRecord.from(
                postId = post.identifier(),
                summary = StructuredSummary.validated(
                    headline = headline,
                    bullets = listOf("First point", "Second point", "Third point"),
                    whyItMatters = "It is useful context for Airwallex tracking.",
                    tags = listOf("airwallex"),
                    sourceType = SourceType.BLOG,
                ),
                model = "test-model",
                promptVersion = "test-prompt",
                objectMapper = objectMapper,
                now = summaryCreatedAt,
            ),
        )
    }
}
