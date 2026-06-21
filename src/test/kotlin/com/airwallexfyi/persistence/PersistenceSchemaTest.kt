package com.airwallexfyi.persistence

import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:persistence_schema_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=true",
    ],
)
class PersistenceSchemaTest @Autowired constructor(
    private val postRepository: PostRepository,
    private val jdbcTemplate: JdbcTemplate,
) {
    @Test
    fun `inserts and reads post records by url`() {
        val post = postRepository.save(
            PostRecord(
                url = "https://www.airwallex.com/global/blog/test-post-${System.nanoTime()}",
                sourceType = "blog",
                title = "Test Post",
                description = "A test Airwallex update",
                author = "Airwallex",
                publishedAt = Instant.parse("2026-06-20T00:00:00Z"),
                sitemapLastmod = Instant.parse("2026-06-20T00:01:00Z"),
                discoveredAt = Instant.parse("2026-06-20T00:02:00Z"),
                contentHash = "sha256:test",
                articleBody = "Article body",
                processingStatus = "DISCOVERED",
                createdAt = Instant.parse("2026-06-20T00:03:00Z"),
                updatedAt = Instant.parse("2026-06-20T00:04:00Z"),
            ),
        )

        val found = postRepository.findByUrl(post.url)

        assertThat(found).isNotNull
        assertThat(found?.identifier()).isEqualTo(post.identifier())
        assertThat(found?.sourceType).isEqualTo("blog")
        assertThat(found?.contentHash).isEqualTo("sha256:test")
    }

    @Test
    fun `rejects duplicate post urls`() {
        val url = "https://www.airwallex.com/global/newsroom/duplicate-${System.nanoTime()}"

        postRepository.save(PostRecord(url = url, sourceType = "newsroom"))

        assertThatThrownBy {
            postRepository.save(PostRecord(url = url, sourceType = "newsroom"))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `summary and notification tables expose phase three columns`() {
        assertThat(columnsFor("summaries")).containsAll(
            listOf(
                "id",
                "post_id",
                "headline",
                "summary_json",
                "why_it_matters",
                "tags_json",
                "model",
                "prompt_version",
                "created_at",
                "updated_at",
            ),
        )
        assertThat(columnsFor("notification_attempts")).containsAll(
            listOf(
                "id",
                "post_id",
                "channel",
                "recipient",
                "status",
                "provider_message_id",
                "error_message",
                "attempted_at",
                "sent_at",
                "created_at",
                "updated_at",
            ),
        )
        assertThat(uniqueConstraintsFor("summaries")).contains("uq_summaries_post_id")
        assertThat(uniqueConstraintsFor("notification_attempts")).contains("uq_notification_attempts_post_channel_recipient")
    }

    @Test
    fun `post records can use every lifecycle processing status`() {
        ProcessingStatus.entries.forEach { status ->
            val post = postRepository.save(
                PostRecord(
                    url = "https://www.airwallex.com/global/blog/status-${status.name.lowercase()}-${System.nanoTime()}",
                    sourceType = "BLOG",
                    processingStatus = status.name,
                ),
            )

            assertThat(postRepository.findByUrl(post.url)?.processingStatus).isEqualTo(status.name)
        }
        assertThat(ProcessingStatus.valueOf("APPROVAL_NEEDED")).isEqualTo(ProcessingStatus.APPROVAL_NEEDED)
    }

    private fun columnsFor(table: String): Set<String> = jdbcTemplate.queryForList(
        """
        SELECT column_name
        FROM information_schema.columns
        WHERE table_name = ?
        """.trimIndent(),
        String::class.java,
        table,
    ).filterNotNull().toSet()

    private fun uniqueConstraintsFor(table: String): Set<String> = jdbcTemplate.queryForList(
        """
        SELECT constraint_name
        FROM information_schema.table_constraints
        WHERE table_name = ? AND constraint_type = 'UNIQUE'
        """.trimIndent(),
        String::class.java,
        table,
    ).filterNotNull().toSet()
}
