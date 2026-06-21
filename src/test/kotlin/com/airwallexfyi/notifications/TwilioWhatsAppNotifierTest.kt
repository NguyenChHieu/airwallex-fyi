package com.airwallexfyi.notifications

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

@SpringBootTest(
    properties = [
        "airwallex-fyi.dry-run=false",
        "spring.datasource.url=jdbc:h2:mem:twilio_whatsapp_notifier_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    ],
)
class TwilioWhatsAppNotifierTest @Autowired constructor(
    private val postRepository: PostRepository,
    private val notificationAttemptRepository: NotificationAttemptRepository,
) {
    @BeforeEach
    fun deleteRows() {
        notificationAttemptRepository.deleteAll()
        postRepository.deleteAll()
    }

    @Test
    fun `twilio success records sent attempt and provider sid`() {
        val transport = FakeTwilioTransport(response = TwilioSendResponse("SM123"))
        val notifier = notifier(transport)
        val post = postRepository.save(post())
        val payload = payload(post.url)

        val result = notifier.send(post, payload)

        assertThat(result.status).isEqualTo(NotificationStatus.SENT)
        assertThat(result.providerMessageId).isEqualTo("SM123")
        assertThat(result.twilioCalled).isTrue()
        assertThat(transport.accountSid).isEqualTo("AC123")
        assertThat(transport.authToken).isEqualTo("secret-token")
        assertThat(transport.from).isEqualTo("whatsapp:+14155238886")
        assertThat(transport.to).isEqualTo("whatsapp:+15550000002")
        assertThat(transport.body).isEqualTo(payload.body)
        val attempt = notificationAttemptRepository.findByPostIdAndChannelAndRecipient(post.identifier(), "whatsapp", payload.recipient)
        assertThat(attempt?.status).isEqualTo("SENT")
        assertThat(attempt?.providerMessageId).isEqualTo("SM123")
        assertThat(attempt?.errorMessage).isNull()
    }

    @Test
    fun `twilio failure records sanitized failure without retry`() {
        val transport = FakeTwilioTransport(failure = IllegalStateException("upstream rejected secret-token for account"))
        val notifier = notifier(transport)
        val post = postRepository.save(post(url = "https://www.airwallex.com/global/blog/twilio-fails-${System.nanoTime()}"))
        val payload = payload(post.url)

        val result = notifier.send(post, payload)

        assertThat(result.status).isEqualTo(NotificationStatus.FAILED)
        assertThat(result.twilioCalled).isTrue()
        assertThat(result.errorMessage).contains("[redacted]")
        assertThat(result.errorMessage).doesNotContain("secret-token")
        assertThat(transport.calls).isEqualTo(1)
        val attempt = notificationAttemptRepository.findByPostIdAndChannelAndRecipient(post.identifier(), "whatsapp", payload.recipient)
        assertThat(attempt?.status).isEqualTo("FAILED")
        assertThat(attempt?.providerMessageId).isNull()
        assertThat(attempt?.errorMessage).isEqualTo(result.errorMessage)
    }

    private fun notifier(transport: FakeTwilioTransport): TwilioWhatsAppNotifier = TwilioWhatsAppNotifier(
        properties = AppProperties(
            twilio = AppProperties.Twilio(
                accountSid = "AC123",
                authToken = "secret-token",
                whatsappFrom = "whatsapp:+14155238886",
            ),
        ),
        notificationAttemptRepository = notificationAttemptRepository,
        transport = transport,
    )

    private fun payload(url: String): WhatsAppAlertPayload = WhatsAppAlertPayload(
        channel = "whatsapp",
        recipient = "whatsapp:+15550000002",
        body = "Airwallex FYI: Test\nLink: $url",
        sourceUrl = url,
        preview = "Airwallex FYI: Test\nLink: $url",
    )

    private fun post(url: String = "https://www.airwallex.com/global/blog/twilio-${System.nanoTime()}"): PostRecord = PostRecord(
        url = url,
        sourceType = SourceType.BLOG.name,
        discoveredAt = Instant.parse("2026-06-20T00:00:00Z"),
    )

    private class FakeTwilioTransport(
        private val response: TwilioSendResponse? = null,
        private val failure: RuntimeException? = null,
    ) : TwilioTransport {
        var calls = 0
        lateinit var accountSid: String
        lateinit var authToken: String
        lateinit var from: String
        lateinit var to: String
        lateinit var body: String

        override fun sendMessage(accountSid: String, authToken: String, from: String, to: String, body: String): TwilioSendResponse {
            calls += 1
            this.accountSid = accountSid
            this.authToken = authToken
            this.from = from
            this.to = to
            this.body = body
            failure?.let { throw it }
            return requireNotNull(response)
        }
    }
}
