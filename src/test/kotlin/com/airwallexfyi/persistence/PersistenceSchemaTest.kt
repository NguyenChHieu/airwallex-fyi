package com.airwallexfyi.persistence

import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
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
    fun `subscriber tables expose phase three one columns`() {
        assertThat(columnsFor("subscribers")).containsAll(
            listOf("id", "display_name", "status", "created_at", "updated_at"),
        )
        assertThat(columnsFor("subscriber_channels")).containsAll(
            listOf("id", "subscriber_id", "channel", "recipient", "status", "created_at", "updated_at"),
        )
        assertThat(uniqueConstraintsFor("subscriber_channels")).contains("uq_subscriber_channels_channel_recipient")
        assertThat(foreignKeysFor("subscriber_channels")).contains("fk_subscriber_channels_subscriber")
    }

    @Test
    fun `subscriber channel schema rejects duplicate recipients and missing subscribers`() {
        val subscriberId = UUID.randomUUID()
        val channelId = UUID.randomUUID()
        val recipient = "whatsapp:+1555${System.nanoTime()}"

        jdbcTemplate.update(
            """
            INSERT INTO subscribers (id, display_name, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            subscriberId,
            "Test Subscriber",
            "ACTIVE",
            Instant.parse("2026-06-22T00:00:00Z"),
            Instant.parse("2026-06-22T00:00:00Z"),
        )
        jdbcTemplate.update(
            """
            INSERT INTO subscriber_channels (id, subscriber_id, channel, recipient, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            channelId,
            subscriberId,
            "whatsapp",
            recipient,
            "ACTIVE",
            Instant.parse("2026-06-22T00:00:01Z"),
            Instant.parse("2026-06-22T00:00:01Z"),
        )

        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO subscriber_channels (id, subscriber_id, channel, recipient, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                UUID.randomUUID(),
                subscriberId,
                "whatsapp",
                recipient,
                "ACTIVE",
                Instant.parse("2026-06-22T00:00:02Z"),
                Instant.parse("2026-06-22T00:00:02Z"),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)

        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO subscriber_channels (id, subscriber_id, channel, recipient, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "whatsapp",
                "whatsapp:+1666${System.nanoTime()}",
                "ACTIVE",
                Instant.parse("2026-06-22T00:00:03Z"),
                Instant.parse("2026-06-22T00:00:03Z"),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `digest delivery tables expose phase three one columns`() {
        assertThat(columnsFor("digest_deliveries")).containsAll(
            listOf(
                "id",
                "subscriber_channel_id",
                "local_date",
                "message_type",
                "status",
                "recipient",
                "channel",
                "payload_preview",
                "provider_message_id",
                "error_message",
                "attempted_at",
                "sent_at",
                "created_at",
                "updated_at",
            ),
        )
        assertThat(columnsFor("digest_delivery_posts")).containsAll(
            listOf("id", "digest_delivery_id", "post_id", "summary_id", "display_order", "created_at"),
        )
        assertThat(uniqueConstraintsFor("digest_deliveries")).contains("uq_digest_deliveries_channel_date")
        assertThat(uniqueConstraintsFor("digest_delivery_posts")).contains("uq_digest_delivery_posts_delivery_post")
        assertThat(foreignKeysFor("digest_deliveries")).contains("fk_digest_deliveries_subscriber_channel")
        assertThat(foreignKeysFor("digest_delivery_posts")).containsAll(
            listOf(
                "fk_digest_delivery_posts_delivery",
                "fk_digest_delivery_posts_post",
                "fk_digest_delivery_posts_summary",
            ),
        )
    }

    @Test
    fun `digest schema rejects duplicate daily deliveries and duplicate post links`() {
        val subscriberId = insertSubscriber("Digest Subscriber")
        val channelId = insertSubscriberChannel(subscriberId, "whatsapp:+1777${System.nanoTime()}")
        val deliveryId = UUID.randomUUID()
        val localDate = LocalDate.of(2026, 6, 22)
        val now = Instant.parse("2026-06-22T00:00:00Z")

        insertDigestDelivery(deliveryId, channelId, localDate, now)

        assertThatThrownBy {
            insertDigestDelivery(UUID.randomUUID(), channelId, localDate, now.plusSeconds(60))
        }.isInstanceOf(DataIntegrityViolationException::class.java)

        assertThatThrownBy {
            insertDigestDelivery(UUID.randomUUID(), UUID.randomUUID(), localDate.plusDays(1), now.plusSeconds(120))
        }.isInstanceOf(DataIntegrityViolationException::class.java)

        val post = postRepository.save(
            PostRecord(
                url = "https://www.airwallex.com/global/blog/digest-link-${System.nanoTime()}",
                sourceType = "BLOG",
                processingStatus = ProcessingStatus.SUMMARY_READY.name,
            ),
        )
        val summaryId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO summaries (id, post_id, headline, summary_json, why_it_matters, tags_json, model, prompt_version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            summaryId,
            post.identifier(),
            "Digest headline",
            "{}",
            "Useful update",
            "[]",
            "test-model",
            "test-prompt",
            now,
            now,
        )
        jdbcTemplate.update(
            """
            INSERT INTO digest_delivery_posts (id, digest_delivery_id, post_id, summary_id, display_order, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            deliveryId,
            post.identifier(),
            summaryId,
            0,
            now,
        )

        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO digest_delivery_posts (id, digest_delivery_id, post_id, summary_id, display_order, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                UUID.randomUUID(),
                deliveryId,
                post.identifier(),
                summaryId,
                1,
                now,
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
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

    private fun insertSubscriber(displayName: String): UUID {
        val subscriberId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO subscribers (id, display_name, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            subscriberId,
            displayName,
            "ACTIVE",
            Instant.parse("2026-06-22T00:00:00Z"),
            Instant.parse("2026-06-22T00:00:00Z"),
        )
        return subscriberId
    }

    private fun insertSubscriberChannel(subscriberId: UUID, recipient: String): UUID {
        val channelId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO subscriber_channels (id, subscriber_id, channel, recipient, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            channelId,
            subscriberId,
            "whatsapp",
            recipient,
            "ACTIVE",
            Instant.parse("2026-06-22T00:00:00Z"),
            Instant.parse("2026-06-22T00:00:00Z"),
        )
        return channelId
    }

    private fun insertDigestDelivery(deliveryId: UUID, channelId: UUID, localDate: LocalDate, attemptedAt: Instant) {
        jdbcTemplate.update(
            """
            INSERT INTO digest_deliveries (
                id, subscriber_channel_id, local_date, message_type, status, recipient, channel,
                payload_preview, provider_message_id, error_message, attempted_at, sent_at, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            deliveryId,
            channelId,
            localDate,
            "NO_CHANGES",
            "DRY_RUN",
            "whatsapp:+17770000000",
            "whatsapp",
            "Airwallex FYI: No new public Blog or Newsroom updates today.",
            null,
            null,
            attemptedAt,
            attemptedAt,
            attemptedAt,
            attemptedAt,
        )
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

    private fun foreignKeysFor(table: String): Set<String> = jdbcTemplate.queryForList(
        """
        SELECT constraint_name
        FROM information_schema.table_constraints
        WHERE table_name = ? AND constraint_type = 'FOREIGN KEY'
        """.trimIndent(),
        String::class.java,
        table,
    ).filterNotNull().toSet()
}
