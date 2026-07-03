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
            appendLine("Airwallex FYI - Daily Brief")
            appendLine(localDate)
            appendLine("${items.size} Airwallex ${"update".pluralized(items.size)} worth noting.")
            items.forEachIndexed { index, item ->
                val summary = item.summary.toStructuredSummary(objectMapper)
                val section = buildString {
                    appendLine()
                    appendLine("${index + 1}. ${summary.headline.cleanInline()}")
                    summary.bullets.forEach { bullet ->
                        appendLine("- ${bullet.cleanInline()}")
                    }
                    appendLine()
                    appendLine("Why it matters:")
                    appendLine(summary.whyItMatters.cleanInline())
                    appendLine()
                    appendLine("Read: ${item.post.url}")
                }
                val remainingCount = items.size - index - 1
                val omittedLine = if (remainingCount > 0) "\n+ $remainingCount more update(s) omitted to keep this brief." else ""
                if (length + section.length + omittedLine.length > maxBodyCharsFor(recipient)) {
                    appendOmittedLineIfFits(remainingCount + 1, maxBodyCharsFor(recipient))
                    return@buildString
                }
                append(section)
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

    private fun StringBuilder.appendOmittedLineIfFits(omittedCount: Int, maxBodyChars: Int) {
        if (omittedCount <= 0) return
        val line = "\n\n+ $omittedCount more update(s) omitted to keep this brief."
        if (length + line.length <= maxBodyChars) {
            append(line)
        }
    }

    private fun String.boundedPreview(): String =
        if (length <= PREVIEW_LIMIT) this else take(PREVIEW_LIMIT - 3) + "..."

    private fun String.pluralized(count: Int): String = if (count == 1) this else "${this}s"

    private fun maxBodyCharsFor(recipient: String): Int =
        if (recipient.startsWith("whatsapp:", ignoreCase = true)) MAX_WHATSAPP_BODY_CHARS else MAX_TELEGRAM_BODY_CHARS

    companion object {
        const val CHANNEL: String = "whatsapp"
        const val NO_CHANGES_TEXT: String = "Airwallex FYI - Daily Brief\nNo new public Blog or Newsroom updates today."
        const val MAX_WHATSAPP_BODY_CHARS = 1500
        const val MAX_TELEGRAM_BODY_CHARS = 3900
        private const val PREVIEW_LIMIT = 500
        private val WHITESPACE = Regex("\\s+")
    }
}
