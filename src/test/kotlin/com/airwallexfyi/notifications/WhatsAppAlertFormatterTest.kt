package com.airwallexfyi.notifications

import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.SourceType
import com.airwallexfyi.summaries.StructuredSummary
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WhatsAppAlertFormatterTest {
    private val formatter = WhatsAppAlertFormatter()

    @Test
    fun `formats exact detailed whatsapp alert body`() {
        val payload = formatter.format(
            post = post(),
            summary = summary(),
            recipient = "whatsapp:+15550000002",
        )

        assertThat(payload.channel).isEqualTo("whatsapp")
        assertThat(payload.recipient).isEqualTo("whatsapp:+15550000002")
        assertThat(payload.sourceUrl).isEqualTo("https://www.airwallex.com/global/blog/platform-update")
        assertThat(payload.body).isEqualTo(
            """
            Airwallex FYI: Airwallex updates its platform
            Source: BLOG
            - New payment tooling shipped
            - Finance teams get faster workflows
            - The update links back to the source
            Why it matters: It points to Airwallex investing in operational finance workflows.
            Tags: payments, platform
            Link: https://www.airwallex.com/global/blog/platform-update
            """.trimIndent(),
        )
        assertThat(payload.preview).isEqualTo(payload.body)
    }

    @Test
    fun `bounds long payload preview`() {
        val summary = StructuredSummary.validated(
            headline = "Airwallex updates its platform",
            bullets = listOf("A".repeat(180), "B".repeat(180), "C".repeat(180)),
            whyItMatters = "D".repeat(180),
            tags = listOf("payments"),
            sourceType = SourceType.BLOG,
        )

        val payload = formatter.format(post(), summary, "whatsapp:+15550000002")

        assertThat(payload.preview).hasSize(500)
        assertThat(payload.preview).endsWith("...")
    }

    private fun summary(): StructuredSummary = StructuredSummary.validated(
        headline = "Airwallex updates its platform",
        bullets = listOf(
            "New payment tooling shipped",
            "Finance teams get faster workflows",
            "The update links back to the source",
        ),
        whyItMatters = "It points to Airwallex investing in operational finance workflows.",
        tags = listOf("payments", "platform"),
        sourceType = SourceType.BLOG,
    )

    private fun post(): PostRecord = PostRecord(
        url = "https://www.airwallex.com/global/blog/platform-update",
        sourceType = SourceType.BLOG.name,
    )
}
