package com.airwallexfyi.notifications

import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.SourceType
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:dry_run_whatsapp_notifier_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    ],
)
class DryRunWhatsAppNotifierTest @Autowired constructor(
    private val postRepository: PostRepository,
    private val notificationAttemptRepository: NotificationAttemptRepository,
) {
    @BeforeEach
    fun deleteRows() {
        notificationAttemptRepository.deleteAll()
        postRepository.deleteAll()
    }

    @Test
    fun `dry run records attempt and never calls twilio`() {
        val notifier = DryRunWhatsAppNotifier(notificationAttemptRepository)
        val post = postRepository.save(post())
        val payload = payload(post.url)

        val result = notifier.send(post, payload)

        assertThat(result.status).isEqualTo(NotificationStatus.DRY_RUN)
        assertThat(result.payloadPreview).isEqualTo(payload.preview)
        assertThat(result.twilioCalled).isFalse()
        assertThat(result.providerMessageId).isNull()
        val attempt = notificationAttemptRepository.findByPostIdAndChannelAndRecipient(
            post.identifier(),
            "whatsapp",
            "whatsapp:+15550000002",
        )
        assertThat(attempt).isNotNull
        assertThat(attempt?.status).isEqualTo("DRY_RUN")
        assertThat(attempt?.providerMessageId).isNull()
        assertThat(attempt?.errorMessage).isNull()
    }

    @Test
    fun `unique notification attempt prevents duplicate alert attempt for recipient`() {
        val notifier = DryRunWhatsAppNotifier(notificationAttemptRepository)
        val post = postRepository.save(post(url = "https://www.airwallex.com/global/blog/dry-run-duplicate-${System.nanoTime()}"))
        val payload = payload(post.url)

        notifier.send(post, payload)

        assertThatThrownBy { notifier.send(post, payload) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun payload(url: String): WhatsAppAlertPayload = WhatsAppAlertPayload(
        channel = "whatsapp",
        recipient = "whatsapp:+15550000002",
        body = "Airwallex FYI: Test\nLink: $url",
        sourceUrl = url,
        preview = "Airwallex FYI: Test\nLink: $url",
    )

    private fun post(url: String = "https://www.airwallex.com/global/blog/dry-run-${System.nanoTime()}"): PostRecord = PostRecord(
        url = url,
        sourceType = SourceType.BLOG.name,
        discoveredAt = Instant.parse("2026-06-20T00:00:00Z"),
    )
}
