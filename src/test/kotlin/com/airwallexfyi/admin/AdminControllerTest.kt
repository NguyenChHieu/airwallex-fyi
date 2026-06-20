package com.airwallexfyi.admin

import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import java.time.Instant
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
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
    @Test
    fun `health endpoint returns safe operational flags`() {
        mockMvc.perform(authorized(get("/admin/health")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.dryRun").value(true))
            .andExpect(jsonPath("$.schedulerEnabled").value(false))
    }

    @Test
    fun `recent posts endpoint returns persisted posts`() {
        val url = "https://www.airwallex.com/global/blog/admin-recent-${System.nanoTime()}"

        postRepository.save(
            PostRecord(
                url = url,
                sourceType = "blog",
                title = "Admin Recent Post",
                description = "Visible through admin endpoint",
                author = "Airwallex",
                publishedAt = Instant.parse("2026-06-20T00:00:00Z"),
                sitemapLastmod = Instant.parse("2026-06-20T00:01:00Z"),
                discoveredAt = Instant.parse("2026-06-20T00:02:00Z"),
                contentHash = "sha256:admin-recent",
                processingStatus = "DISCOVERED",
            ),
        )

        mockMvc.perform(authorized(get("/admin/posts/recent")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.posts[0].url").value(url))
            .andExpect(jsonPath("$.posts[0].sourceType").value("blog"))
            .andExpect(jsonPath("$.posts[0].processingStatus").value("DISCOVERED"))
    }

    @Test
    fun `run once endpoint returns phase one stub response`() {
        mockMvc.perform(authorized(post("/admin/run-once")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("stubbed"))
            .andExpect(jsonPath("$.externalCallsTriggered").value(false))
            .andExpect(jsonPath("$.message").value("Phase 1 run-once stub completed; no Airwallex, OpenAI, or Twilio calls were made."))
    }

    private fun authorized(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder): org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder =
        builder.header(AdminTokenFilter.ADMIN_TOKEN_HEADER, "test-admin-token")
}