package com.airwallexfyi.digests

import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.posts.SourceType
import com.airwallexfyi.summaries.StructuredSummary
import com.airwallexfyi.summaries.SummaryRecord
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

class DailyDigestFormatterTest {
    private val objectMapper = ObjectMapper()
    private val formatter = DailyDigestFormatter(objectMapper)

    @Test
    fun `formats one combined digest for multiple posts`() {
        val first = item(
            url = "https://www.airwallex.com/global/blog/first-update",
            headline = "First Airwallex update",
            bullets = listOf("First useful point", "Second useful point", "Third useful point"),
            whyItMatters = "It helps teams track payment operations.",
        )
        val second = item(
            url = "https://www.airwallex.com/global/newsroom/second-update",
            headline = "Second Airwallex update",
            bullets = listOf("Fourth useful point", "Fifth useful point", "Sixth useful point"),
            whyItMatters = "It signals where Airwallex is investing.",
            sourceType = SourceType.NEWSROOM,
        )

        val payload = formatter.formatDigest(listOf(first, second), "whatsapp:+15550000002", LocalDate.of(2026, 6, 22))

        assertThat(payload.channel).isEqualTo("whatsapp")
        assertThat(payload.recipient).isEqualTo("whatsapp:+15550000002")
        assertThat(payload.sourceUrl).isNull()
        assertThat(payload.body).contains("Airwallex FYI - 2026-06-22")
        assertThat(payload.body).contains("1. First Airwallex update")
        assertThat(payload.body).contains("2. Second Airwallex update")
        assertThat(payload.body).contains("- First useful point")
        assertThat(payload.body).contains("- Second useful point")
        assertThat(payload.body).doesNotContain("Third useful point")
        assertThat(payload.body).contains("Why it matters: It helps teams track payment operations.")
        assertThat(payload.body).contains("Link: https://www.airwallex.com/global/blog/first-update")
        assertThat(payload.body).contains("Link: https://www.airwallex.com/global/newsroom/second-update")
        assertThat(payload.preview).isEqualTo(payload.body)
    }

    @Test
    fun `formats exact no change message`() {
        val payload = formatter.formatNoChanges("whatsapp:+15550000002")

        assertThat(payload.body).isEqualTo("Airwallex FYI: No new public Blog or Newsroom updates today.")
        assertThat(payload.preview).isEqualTo(payload.body)
        assertThat(payload.sourceUrl).isNull()
    }

    @Test
    fun `rejects non summary-ready digest items`() {
        val approvalNeeded = item(
            url = "https://www.airwallex.com/global/blog/approval-needed",
            headline = "Needs approval",
            bullets = listOf("One", "Two", "Three"),
            whyItMatters = "It should stay admin-only.",
            processingStatus = ProcessingStatus.APPROVAL_NEEDED.name,
        )

        assertThatThrownBy {
            formatter.formatDigest(listOf(approvalNeeded), "whatsapp:+15550000002", LocalDate.of(2026, 6, 22))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("summary-ready")
    }

    @Test
    fun `bounds long digest preview`() {
        val payload = formatter.formatDigest(
            listOf(
                item(
                    url = "https://www.airwallex.com/global/blog/long-update",
                    headline = "A".repeat(180),
                    bullets = listOf("B".repeat(180), "C".repeat(180), "D".repeat(180)),
                    whyItMatters = "E".repeat(180),
                ),
            ),
            "whatsapp:+15550000002",
            LocalDate.of(2026, 6, 22),
        )

        assertThat(payload.preview).hasSize(500)
        assertThat(payload.preview).endsWith("...")
    }

    private fun item(
        url: String,
        headline: String,
        bullets: List<String>,
        whyItMatters: String,
        sourceType: SourceType = SourceType.BLOG,
        processingStatus: String = ProcessingStatus.SUMMARY_READY.name,
    ): DigestEligibleSummary {
        val post = PostRecord(
            url = url,
            sourceType = sourceType.name,
            processingStatus = processingStatus,
        )
        val summary = StructuredSummary.validated(
            headline = headline,
            bullets = bullets,
            whyItMatters = whyItMatters,
            tags = listOf("airwallex"),
            sourceType = sourceType,
        )
        return DigestEligibleSummary(
            post = post,
            summary = SummaryRecord.from(
                postId = post.identifier(),
                summary = summary,
                model = "test-model",
                promptVersion = "test-prompt",
                objectMapper = objectMapper,
            ),
        )
    }
}
