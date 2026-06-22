package com.airwallexfyi.notifications

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "airwallex-fyi", name = ["dry-run"], havingValue = "true", matchIfMissing = true)
class DryRunWhatsAppNotifier : WhatsAppNotifier {
    override fun send(payload: WhatsAppAlertPayload): NotificationResult = NotificationResult(
        status = NotificationStatus.DRY_RUN,
        payloadPreview = payload.preview,
        twilioCalled = false,
    )
}
