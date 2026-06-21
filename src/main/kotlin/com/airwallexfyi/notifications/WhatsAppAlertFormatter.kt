package com.airwallexfyi.notifications

import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.summaries.StructuredSummary
import org.springframework.stereotype.Component

@Component
class WhatsAppAlertFormatter {
    fun format(post: PostRecord, summary: StructuredSummary, recipient: String): WhatsAppAlertPayload {
        val body = buildString {
            appendLine("Airwallex FYI: ${summary.headline.cleanInline()}")
            appendLine("Source: ${summary.sourceType.name}")
            summary.bullets.forEach { bullet -> appendLine("- ${bullet.cleanInline()}") }
            appendLine("Why it matters: ${summary.whyItMatters.cleanInline()}")
            appendLine("Tags: ${summary.tags.joinToString(", ") { it.cleanInline() }}")
            append("Link: ${post.url}")
        }

        return WhatsAppAlertPayload(
            channel = CHANNEL,
            recipient = recipient,
            body = body,
            sourceUrl = post.url,
            preview = body.boundedPreview(),
        )
    }

    private fun String.cleanInline(): String = trim().replace(WHITESPACE, " ")

    private fun String.boundedPreview(): String =
        if (length <= PREVIEW_LIMIT) this else take(PREVIEW_LIMIT - 3) + "..."

    companion object {
        const val CHANNEL = "whatsapp"
        private const val PREVIEW_LIMIT = 500
        private val WHITESPACE = Regex("\\s+")
    }
}
