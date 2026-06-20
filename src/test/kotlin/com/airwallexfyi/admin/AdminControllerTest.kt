package com.airwallexfyi.admin

import com.airwallexfyi.monitor.MonitorRunError
import com.airwallexfyi.monitor.MonitorRunResult
import com.airwallexfyi.monitor.MonitorRunSampleUrls
import com.airwallexfyi.monitor.MonitorRunService
import com.airwallexfyi.monitor.MonitorRunStatus
import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.posts.SourceType
import java.time.Instant
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
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
) {
    @MockitoBean
    lateinit var monitorRunService: MonitorRunService

    @BeforeEach
    fun setUp() {
        postRepository.deleteAll()
        `when`(monitorRunService.runOnce()).thenReturn(completedRunResult())
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
            .andExpect(jsonPath("$.sampleUrls.seeded[0]").value("https://www.airwallex.com/global/blog/seeded"))
            .andExpect(jsonPath("$.sampleUrls.new[0]").value("https://www.airwallex.com/global/blog/new"))
            .andExpect(jsonPath("$.sampleUrls.updated[0]").value("https://www.airwallex.com/global/newsroom/updated"))
            .andExpect(jsonPath("$.sampleErrors", hasSize<Any>(0)))
            .andExpect(jsonPath("$.externalCallsTriggered").value(false))
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
        val blogSeeded = post(
            url = "https://www.airwallex.com/global/blog/filter-blog-seeded",
            sourceType = SourceType.BLOG,
            processingStatus = ProcessingStatus.SEEDED,
            discoveredAt = Instant.parse("2026-06-20T00:02:00Z"),
        )
        val newsroomDiscovered = post(
            url = "https://www.airwallex.com/global/newsroom/filter-newsroom-discovered",
            sourceType = SourceType.NEWSROOM,
            processingStatus = ProcessingStatus.DISCOVERED,
            discoveredAt = Instant.parse("2026-06-20T00:01:00Z"),
        )
        postRepository.saveAll(listOf(blogDiscovered, blogSeeded, newsroomDiscovered))

        mockMvc.perform(authorized(get("/admin/posts/recent").param("sourceType", "BLOG")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").value(2))
            .andExpect(jsonPath("$.posts[0].sourceType").value("BLOG"))
            .andExpect(jsonPath("$.posts[1].sourceType").value("BLOG"))

        mockMvc.perform(authorized(get("/admin/posts/recent").param("processingStatus", "SEEDED")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.posts[0].url").value(blogSeeded.url))

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

    private fun completedRunResult(): MonitorRunResult = MonitorRunResult(
        status = MonitorRunStatus.COMPLETED,
        sitemapFetched = true,
        discoveredCount = 12,
        seededCount = 1,
        newCount = 2,
        updatedCount = 3,
        skippedCount = 4,
        failedCount = 0,
        sampleUrls = MonitorRunSampleUrls(
            seeded = listOf("https://www.airwallex.com/global/blog/seeded"),
            new = listOf("https://www.airwallex.com/global/blog/new"),
            updated = listOf("https://www.airwallex.com/global/newsroom/updated"),
        ),
        sampleErrors = emptyList(),
        externalCallsTriggered = false,
        message = "Monitor run completed.",
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
