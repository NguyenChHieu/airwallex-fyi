package com.airwallexfyi.digests

import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.summaries.SummaryRepository
import com.airwallexfyi.summaries.toStructuredSummary
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

@Service
class LatestUpdatesService(
    private val summaryRepository: SummaryRepository,
    private val postRepository: PostRepository,
    private val objectMapper: ObjectMapper,
) {
    fun formatLatest(limit: Int = DEFAULT_LIMIT): String {
        val postsById = postRepository.findAll().associateBy { it.identifier() }
        val items = summaryRepository.findAll()
            .asSequence()
            .mapNotNull { summary ->
                postsById[summary.postId]
                    ?.takeIf { post -> post.processingStatus == ProcessingStatus.SUMMARY_READY.name }
                    ?.let { post -> DigestEligibleSummary(post = post, summary = summary) }
            }
            .sortedWith(
                compareByDescending<DigestEligibleSummary> { it.summary.createdAt }
                    .thenByDescending { it.post.publishedAt ?: it.post.discoveredAt }
                    .thenBy { it.post.url },
            )
            .take(limit.coerceIn(1, MAX_LIMIT))
            .toList()

        if (items.isEmpty()) {
            return NO_SUMMARIES_TEXT
        }

        return buildString {
            appendLine("Airwallex FYI - latest updates")
            items.forEachIndexed { index, item ->
                val summary = item.summary.toStructuredSummary(objectMapper)
                appendLine()
                appendLine("${index + 1}. ${summary.headline.boundedInline(MAX_HEADLINE_CHARS)}")
                summary.bullets.take(MAX_BULLETS_PER_POST).forEach { bullet ->
                    appendLine("- ${bullet.boundedInline(MAX_BULLET_CHARS)}")
                }
                appendLine("Read: ${item.post.url}")
            }
        }.trimEnd()
    }

    private fun String.boundedInline(maxLength: Int): String {
        val cleaned = trim().replace(WHITESPACE, " ")
        return if (cleaned.length <= maxLength) cleaned else cleaned.take(maxLength - 3).trimEnd() + "..."
    }

    companion object {
        const val NO_SUMMARIES_TEXT: String = "Airwallex FYI: No summarized updates are available yet."
        private const val DEFAULT_LIMIT = 3
        private const val MAX_LIMIT = 5
        private const val MAX_BULLETS_PER_POST = 2
        private const val MAX_HEADLINE_CHARS = 110
        private const val MAX_BULLET_CHARS = 110
        private val WHITESPACE = Regex("\\s+")
    }
}
