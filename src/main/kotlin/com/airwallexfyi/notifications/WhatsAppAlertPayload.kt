package com.airwallexfyi.notifications

data class WhatsAppAlertPayload(
    val channel: String,
    val recipient: String,
    val body: String,
    val sourceUrl: String,
    val preview: String,
)
