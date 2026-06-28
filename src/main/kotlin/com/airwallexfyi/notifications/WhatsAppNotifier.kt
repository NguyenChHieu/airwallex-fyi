package com.airwallexfyi.notifications

interface WhatsAppNotifier {
    fun send(payload: WhatsAppAlertPayload): NotificationResult
}

data class NotificationResult(
    val status: NotificationStatus,
    val payloadPreview: String,
    val providerMessageId: String? = null,
    val errorMessage: String? = null,
    val twilioCalled: Boolean,
)

enum class NotificationStatus {
    DRY_RUN,
    SKIPPED,
    SENT,
    FAILED,
}
