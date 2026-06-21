package com.airwallexfyi.notifications

import com.airwallexfyi.posts.PostRecord

interface WhatsAppNotifier {
    fun send(post: PostRecord, payload: WhatsAppAlertPayload): NotificationResult
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
    SENT,
    FAILED,
}
