package com.airwallexfyi.notifications

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DryRunWhatsAppNotifierTest {
    @Test
    fun `dry run returns preview and never calls twilio`() {
        val notifier = DryRunWhatsAppNotifier()
        val payload = payload()

        val result = notifier.send(payload)

        assertThat(result.status).isEqualTo(NotificationStatus.DRY_RUN)
        assertThat(result.payloadPreview).isEqualTo(payload.preview)
        assertThat(result.twilioCalled).isFalse()
        assertThat(result.providerMessageId).isNull()
        assertThat(result.errorMessage).isNull()
    }

    private fun payload(): WhatsAppAlertPayload = WhatsAppAlertPayload(
        channel = "whatsapp",
        recipient = "whatsapp:+15550000002",
        body = "Airwallex FYI: Test digest",
        sourceUrl = null,
        preview = "Airwallex FYI: Test digest",
    )
}
