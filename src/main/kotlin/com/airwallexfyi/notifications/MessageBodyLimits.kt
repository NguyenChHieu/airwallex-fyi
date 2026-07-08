package com.airwallexfyi.notifications

object MessageBodyLimits {
    const val WHATSAPP: Int = 1500
    const val TELEGRAM: Int = 3900

    fun forRecipient(recipient: String): Int =
        if (recipient.startsWith("whatsapp:", ignoreCase = true)) WHATSAPP else TELEGRAM
}
