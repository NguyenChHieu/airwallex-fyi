package com.airwallexfyi.notifications

import com.airwallexfyi.config.AppProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TwilioWhatsAppNotifierTest {
    @Test
    fun `twilio success sends combined digest body and provider sid`() {
        val transport = FakeTwilioTransport(response = TwilioSendResponse("SM123"))
        val notifier = notifier(transport)
        val payload = payload()

        val result = notifier.send(payload)

        assertThat(result.status).isEqualTo(NotificationStatus.SENT)
        assertThat(result.providerMessageId).isEqualTo("SM123")
        assertThat(result.twilioCalled).isTrue()
        assertThat(transport.accountSid).isEqualTo("AC123")
        assertThat(transport.authToken).isEqualTo("secret-token")
        assertThat(transport.from).isEqualTo("whatsapp:+14155238886")
        assertThat(transport.to).isEqualTo("whatsapp:+15550000002")
        assertThat(transport.body).isEqualTo(payload.body)
    }

    @Test
    fun `twilio failure returns sanitized failure without retry`() {
        val transport = FakeTwilioTransport(failure = IllegalStateException("upstream rejected secret-token for account"))
        val notifier = notifier(transport)
        val payload = payload()

        val result = notifier.send(payload)

        assertThat(result.status).isEqualTo(NotificationStatus.FAILED)
        assertThat(result.twilioCalled).isTrue()
        assertThat(result.errorMessage).contains("[redacted]")
        assertThat(result.errorMessage).doesNotContain("secret-token")
        assertThat(transport.calls).isEqualTo(1)
    }

    private fun notifier(transport: FakeTwilioTransport): TwilioWhatsAppNotifier = TwilioWhatsAppNotifier(
        properties = AppProperties(
            twilio = AppProperties.Twilio(
                accountSid = "AC123",
                authToken = "secret-token",
                whatsappFrom = "whatsapp:+14155238886",
            ),
        ),
        transport = transport,
    )

    private fun payload(): WhatsAppAlertPayload = WhatsAppAlertPayload(
        channel = "whatsapp",
        recipient = "whatsapp:+15550000002",
        body = "Airwallex FYI - 2026-06-22\n\n1. First update\n- Useful point\nWhy it matters: It matters\nLink: https://example.com",
        sourceUrl = null,
        preview = "Airwallex FYI - 2026-06-22",
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
