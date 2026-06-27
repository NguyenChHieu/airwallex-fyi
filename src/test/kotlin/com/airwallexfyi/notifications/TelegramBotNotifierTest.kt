package com.airwallexfyi.notifications

import com.airwallexfyi.config.AppProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TelegramBotNotifierTest {
    @Test
    fun `telegram success sends digest body and provider message id`() {
        val transport = FakeTelegramTransport(response = TelegramSendResponse("42"))
        val notifier = notifier(transport)
        val payload = payload()

        val result = notifier.send(payload)

        assertThat(result.status).isEqualTo(NotificationStatus.SENT)
        assertThat(result.providerMessageId).isEqualTo("42")
        assertThat(result.twilioCalled).isFalse()
        assertThat(transport.botToken).isEqualTo("secret-token")
        assertThat(transport.chatId).isEqualTo("123456789")
        assertThat(transport.body).isEqualTo(payload.body)
    }

    @Test
    fun `telegram failure returns sanitized failure without retry`() {
        val transport = FakeTelegramTransport(failure = IllegalStateException("upstream rejected secret-token"))
        val notifier = notifier(transport)

        val result = notifier.send(payload())

        assertThat(result.status).isEqualTo(NotificationStatus.FAILED)
        assertThat(result.twilioCalled).isFalse()
        assertThat(result.errorMessage).contains("[redacted]")
        assertThat(result.errorMessage).doesNotContain("secret-token")
        assertThat(transport.calls).isEqualTo(1)
    }

    private fun notifier(transport: FakeTelegramTransport): TelegramBotNotifier = TelegramBotNotifier(
        properties = AppProperties(
            telegram = AppProperties.Telegram(
                botToken = "secret-token",
                chatId = "123456789",
            ),
        ),
        transport = transport,
    )

    private fun payload(): WhatsAppAlertPayload = WhatsAppAlertPayload(
        channel = "telegram",
        recipient = "123456789",
        body = "Airwallex FYI - 2026-06-22\n\n1. First update\n- Useful point\nWhy it matters: It matters\nLink: https://example.com",
        sourceUrl = null,
        preview = "Airwallex FYI - 2026-06-22",
    )

    private class FakeTelegramTransport(
        private val response: TelegramSendResponse? = null,
        private val failure: RuntimeException? = null,
    ) : TelegramTransport {
        var calls = 0
        lateinit var botToken: String
        lateinit var chatId: String
        lateinit var body: String

        override fun sendMessage(botToken: String, chatId: String, body: String): TelegramSendResponse {
            calls += 1
            this.botToken = botToken
            this.chatId = chatId
            this.body = body
            failure?.let { throw it }
            return requireNotNull(response)
        }

        override fun getUpdates(botToken: String, offset: Long?): List<TelegramUpdate> = emptyList()
    }
}
