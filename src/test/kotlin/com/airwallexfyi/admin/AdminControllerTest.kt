package com.airwallexfyi.admin

import com.airwallexfyi.monitor.MonitorApprovalNeeded
import com.airwallexfyi.monitor.MonitorRunError
import com.airwallexfyi.monitor.MonitorRunResult
import com.airwallexfyi.monitor.MonitorRunSampleUrls
import com.airwallexfyi.monitor.MonitorRunService
import com.airwallexfyi.monitor.MonitorRunStatus
import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.posts.SourceType
import com.airwallexfyi.summaries.AiSummaryClient
import com.airwallexfyi.summaries.SummaryGenerationException
import com.airwallexfyi.summaries.SummaryRepository
import com.airwallexfyi.summaries.StructuredSummary
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "airwallex-fyi.admin.token=test-admin-token",
        "spring.datasource.url=jdbc:h2:mem:admin_controller_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    ],
)
@AutoConfigureMockMvc
class AdminControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val postRepository: PostRepository,
    private val summaryRepository: SummaryRepository,
    private val fakeAiSummaryClient: MutableAiSummaryClient,
) {
    @MockitoBean
    lateinit var monitorRunService: MonitorRunService


    @BeforeEach
    fun setUp() {
        summaryRepository.deleteAll()
        postRepository.deleteAll()
        `when`(monitorRunService.runOnce()).thenReturn(completedRunResult())
        fakeAiSummaryClient.reset()
    }

    @Test
    fun `health endpoint returns safe operational flags`() {
        mockMvc.perform(authorized(get("/admin/health")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.dryRun").value(true))
            .andExpect(jsonPath("$.schedulerEnabled").value(false))
    }

    @Test
    fun `run once endpoint returns operational counts and samples`() {
        mockMvc.perform(authorized(post("/admin/run-once")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.message").value("Monitor run completed."))
            .andExpect(jsonPath("$.sitemapFetched").value(true))
            .andExpect(jsonPath("$.discoveredCount").value(12))
            .andExpect(jsonPath("$.seededCount").value(1))
            .andExpect(jsonPath("$.newCount").value(2))
            .andExpect(jsonPath("$.updatedCount").value(3))
            .andExpect(jsonPath("$.skippedCount").value(4))
            .andExpect(jsonPath("$.failedCount").value(0))
            .andExpect(jsonPath("$.summarizedCount").value(2))
            .andExpect(jsonPath("$.summaryFailedCount").value(0))
            .andExpect(jsonPath("$.approvalNeededCount").value(1))
            .andExpect(jsonPath("$.digestSentCount").value(1))
            .andExpect(jsonPath("$.digestNoChangeCount").value(1))
            .andExpect(jsonPath("$.digestSkippedDuplicateCount").value(1))
            .andExpect(jsonPath("$.digestFailedCount").value(0))
            .andExpect(jsonPath("$.sampleUrls.seeded[0]").value("https://www.airwallex.com/global/blog/seeded"))
            .andExpect(jsonPath("$.sampleUrls.new[0]").value("https://www.airwallex.com/global/blog/new"))
            .andExpect(jsonPath("$.sampleUrls.updated[0]").value("https://www.airwallex.com/global/newsroom/updated"))
            .andExpect(jsonPath("$.samplePayloads[0]").value("Airwallex FYI: Example payload"))
            .andExpect(jsonPath("$.sampleDigestDeliveries[0]").value("whatsapp:+15550000002 DIGEST DRY_RUN"))
            .andExpect(jsonPath("$.sampleDigestErrors", hasSize<Any>(0)))
            .andExpect(jsonPath("$.sampleApprovalNeeded[0].reason").value("content_changed"))
            .andExpect(jsonPath("$.sampleErrors", hasSize<Any>(0)))
            .andExpect(jsonPath("$.externalCallsTriggered").value(true))
            .andExpect(jsonPath("$.twilioCallsTriggered").value(false))
    }

    @Test
    fun `run once endpoint returns sampled partial failure errors`() {
        `when`(monitorRunService.runOnce()).thenReturn(
            completedRunResult().copy(
                status = MonitorRunStatus.PARTIAL_FAILURE,
                message = "Monitor run completed with article-level failures.",
                failedCount = 1,
                sampleErrors = listOf(
                    MonitorRunError(
                        url = "https://www.airwallex.com/global/blog/broken",
                        reason = "missing meaningful body",
                    ),
                ),
            ),
        )

        mockMvc.perform(authorized(post("/admin/run-once")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("partial_failure"))
            .andExpect(jsonPath("$.failedCount").value(1))
            .andExpect(jsonPath("$.sampleErrors[0].url").value("https://www.airwallex.com/global/blog/broken"))
            .andExpect(jsonPath("$.sampleErrors[0].reason").value("missing meaningful body"))
    }

    @Test
    fun `recent posts endpoint returns body previews without full bodies`() {
        val longBody = "Airwallex ".repeat(40)
        val url = "https://www.airwallex.com/global/blog/admin-recent-${System.nanoTime()}"
        postRepository.save(
            post(
                url = url,
                sourceType = SourceType.BLOG,
                processingStatus = ProcessingStatus.DISCOVERED,
                articleBody = longBody,
                discoveredAt = Instant.parse("2026-06-20T00:02:00Z"),
            ),
        )

        mockMvc.perform(authorized(get("/admin/posts/recent")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.posts[0].url").value(url))
            .andExpect(jsonPath("$.posts[0].sourceType").value("BLOG"))
            .andExpect(jsonPath("$.posts[0].processingStatus").value("DISCOVERED"))
            .andExpect(jsonPath("$.posts[0].bodyPreview").value(longBody.trim().take(237) + "..."))
            .andExpect(jsonPath("$.posts[0].articleBody").doesNotExist())
    }

    @Test
    fun `recent posts endpoint filters by source type and processing status`() {
        val blogDiscovered = post(
            url = "https://www.airwallex.com/global/blog/filter-blog-discovered",
            sourceType = SourceType.BLOG,
            processingStatus = ProcessingStatus.DISCOVERED,
            discoveredAt = Instant.parse("2026-06-20T00:03:00Z"),
        )
        val blogApproval = post(
            url = "https://www.airwallex.com/global/blog/filter-blog-approval",
            sourceType = SourceType.BLOG,
            processingStatus = ProcessingStatus.APPROVAL_NEEDED,
            discoveredAt = Instant.parse("2026-06-20T00:02:00Z"),
        )
        val newsroomDiscovered = post(
            url = "https://www.airwallex.com/global/newsroom/filter-newsroom-discovered",
            sourceType = SourceType.NEWSROOM,
            processingStatus = ProcessingStatus.DISCOVERED,
            discoveredAt = Instant.parse("2026-06-20T00:01:00Z"),
        )
        postRepository.saveAll(listOf(blogDiscovered, blogApproval, newsroomDiscovered))

        mockMvc.perform(authorized(get("/admin/posts/recent").param("sourceType", "BLOG")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").value(2))
            .andExpect(jsonPath("$.posts[0].sourceType").value("BLOG"))
            .andExpect(jsonPath("$.posts[1].sourceType").value("BLOG"))

        mockMvc.perform(authorized(get("/admin/posts/recent").param("processingStatus", "APPROVAL_NEEDED")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.posts[0].url").value(blogApproval.url))

        mockMvc.perform(
            authorized(
                get("/admin/posts/recent")
                    .param("sourceType", "NEWSROOM")
                    .param("processingStatus", "DISCOVERED"),
            ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.posts[0].url").value(newsroomDiscovered.url))
    }

    @Test
    fun `recent posts endpoint rejects invalid enum filters`() {
        mockMvc.perform(authorized(get("/admin/posts/recent").param("sourceType", "INVALID")))
            .andExpect(status().isBadRequest)

        mockMvc.perform(authorized(get("/admin/posts/recent").param("processingStatus", "INVALID")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `manual summarize endpoint summarizes approval needed post without notification attempt`() {
        val savedPost = postRepository.save(
            post(
                url = "https://www.airwallex.com/global/blog/manual-summary-${System.nanoTime()}",
                sourceType = SourceType.BLOG,
                processingStatus = ProcessingStatus.APPROVAL_NEEDED,
                articleBody = "Airwallex published a public update with enough text to summarize. ".repeat(4),
                discoveredAt = Instant.parse("2026-06-20T00:05:00Z"),
            ),
        )
        fakeAiSummaryClient.summary = summary()

        mockMvc.perform(authorized(post("/admin/posts/${savedPost.identifier()}/summarize")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.postId").value(savedPost.identifier().toString()))
            .andExpect(jsonPath("$.url").value(savedPost.url))
            .andExpect(jsonPath("$.status").value("SUMMARY_READY"))
            .andExpect(jsonPath("$.headline").value("Airwallex updates its platform"))
            .andExpect(jsonPath("$.tags[0]").value("payments"))
            .andExpect(jsonPath("$.failureReason").doesNotExist())

        val updatedPost = postRepository.findByUrl(savedPost.url)
        assertThat(updatedPost?.processingStatus).isEqualTo("SUMMARY_READY")
        assertThat(summaryRepository.findByPostId(savedPost.identifier())).isNotNull
    }

    @Test
    fun `manual summarize endpoint records bounded summary failure`() {
        val savedPost = postRepository.save(
            post(
                url = "https://www.airwallex.com/global/blog/manual-summary-fails-${System.nanoTime()}",
                sourceType = SourceType.BLOG,
                processingStatus = ProcessingStatus.APPROVAL_NEEDED,
                articleBody = "Airwallex published a public update with enough text to summarize. ".repeat(4),
                discoveredAt = Instant.parse("2026-06-20T00:06:00Z"),
            ),
        )
        fakeAiSummaryClient.failure = SummaryGenerationException("bad json")

        mockMvc.perform(authorized(post("/admin/posts/${savedPost.identifier()}/summarize")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("SUMMARY_FAILED"))
            .andExpect(jsonPath("$.failureReason").value("bad json"))
            .andExpect(jsonPath("$.headline").doesNotExist())

        val updatedPost = postRepository.findByUrl(savedPost.url)
        assertThat(updatedPost?.processingStatus).isEqualTo("SUMMARY_FAILED")
        assertThat(summaryRepository.findByPostId(savedPost.identifier())).isNull()
    }

    @TestConfiguration(proxyBeanMethods = false)
    class FakeAiConfig {
        @Bean
        @Primary
        fun mutableAiSummaryClient(): MutableAiSummaryClient = MutableAiSummaryClient()
    }

    class MutableAiSummaryClient : AiSummaryClient {
        var summary: StructuredSummary? = null
        var failure: RuntimeException? = null

        override fun summarize(article: com.airwallexfyi.articles.ExtractedArticle): StructuredSummary {
            failure?.let { throw it }
            return requireNotNull(summary) { "test summary not configured" }
        }

        fun reset() {
            summary = null
            failure = null
        }
    }
    private fun completedRunResult(): MonitorRunResult = MonitorRunResult(
        status = MonitorRunStatus.COMPLETED,
        sitemapFetched = true,
        discoveredCount = 12,
        seededCount = 1,
        newCount = 2,
        updatedCount = 3,
        skippedCount = 4,
        failedCount = 0,
        summarizedCount = 2,
        summaryFailedCount = 0,
        approvalNeededCount = 1,
        digestSentCount = 1,
        digestNoChangeCount = 1,
        digestSkippedDuplicateCount = 1,
        digestFailedCount = 0,
        sampleUrls = MonitorRunSampleUrls(
            seeded = listOf("https://www.airwallex.com/global/blog/seeded"),
            new = listOf("https://www.airwallex.com/global/blog/new"),
            updated = listOf("https://www.airwallex.com/global/newsroom/updated"),
        ),
        sampleErrors = emptyList(),
        samplePayloads = listOf("Airwallex FYI: Example payload"),
        sampleApprovalNeeded = listOf(
            MonitorApprovalNeeded(
                url = "https://www.airwallex.com/global/newsroom/updated",
                reason = "content_changed",
            ),
        ),
        sampleDigestDeliveries = listOf("whatsapp:+15550000002 DIGEST DRY_RUN"),
        sampleDigestErrors = emptyList(),
        externalCallsTriggered = true,
        twilioCallsTriggered = false,
        message = "Monitor run completed.",
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

    private fun post(
        url: String,
        sourceType: SourceType,
        processingStatus: ProcessingStatus,
        articleBody: String = "Short article body for preview",
        discoveredAt: Instant,
    ): PostRecord = PostRecord(
        url = url,
        sourceType = sourceType.name,
        title = "Admin Recent Post",
        description = "Visible through admin endpoint",
        author = "Airwallex",
        publishedAt = Instant.parse("2026-06-20T00:00:00Z"),
        sitemapLastmod = Instant.parse("2026-06-20T00:01:00Z"),
        discoveredAt = discoveredAt,
        contentHash = "sha256:${url.substringAfterLast('/')}",
        articleBody = articleBody,
        processingStatus = processingStatus.name,
    )

    private fun authorized(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder): org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder =
        builder.header(AdminTokenFilter.ADMIN_TOKEN_HEADER, "test-admin-token")
}



