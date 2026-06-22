package com.airwallexfyi.digests

import com.airwallexfyi.notifications.WhatsAppAlertPayload
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.summaries.toStructuredSummary
import java.time.LocalDate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class DailyDigestFormatter(
    private val objectMapper: ObjectMapper,
) {
    fun formatDigest(items: List<DigestEligibleSummary>, recipient: String, localDate: LocalDate): WhatsAppAlertPayload {
        require(items.isNotEmpty()) { "digest requires at least one eligible post" }
        require(items.all { it.post.processingStatus == ProcessingStatus.SUMMARY_READY.name }) {
            "digest items must be summary-ready"
        }

        val body = buildString {
            appendLine("Airwallex FYI - ${localDate}")
            items.forEachIndexed { index, item ->
                val summary = item.summary.toStructuredSummary(objectMapper)
                appendLine()
                appendLine("${index + 1}. ${summary.headline.cleanInline()}")
                summary.bullets.take(MAX_BULLETS_PER_POST).forEach { bullet ->
                    appendLine("- ${bullet.cleanInline()}")
                }
                appendLine("Why it matters: ${summary.whyItMatters.cleanInline()}")
                appendLine("Link: ${item.post.url}")
            }
        }.trimEnd()

        return WhatsAppAlertPayload(
            channel = CHANNEL,
            recipient = recipient,
            body = body,
            sourceUrl = null,
            preview = body.boundedPreview(),
        )
    }

    fun formatNoChanges(recipient: String): WhatsAppAlertPayload = WhatsAppAlertPayload(
        channel = CHANNEL,
        recipient = recipient,
        body = NO_CHANGES_TEXT,
        sourceUrl = null,
        preview = NO_CHANGES_TEXT,
    )

    private fun String.cleanInline(): String = trim().replace(WHITESPACE, " ")

    private fun String.boundedPreview(): String =
        if (length <= PREVIEW_LIMIT) this else take(PREVIEW_LIMIT - 3) + "..."

    companion object {
        const val CHANNEL: String = "whatsapp"
        const val NO_CHANGES_TEXT: String = "Airwallex FYI: No new public Blog or Newsroom updates today."
        private const val PREVIEW_LIMIT = 500
        private const val MAX_BULLETS_PER_POST = 2
        private val WHITESPACE = Regex("\\s+")
    }
}
