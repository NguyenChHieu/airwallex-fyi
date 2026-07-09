package com.airwallexfyi.digests

import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.notifications.MessageBodyLimits
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
    fun formatLatest(
        limit: Int = DEFAULT_LIMIT,
        maxBodyChars: Int = MessageBodyLimits.TELEGRAM,
    ): String {
        val postsById = postRepository.findAll().associateBy { it.identifier() }
        val items = summaryRepository.findAll()
            .asSequence()
            .mapNotNull { summary ->
                postsById[summary.postId]
                    ?.takeIf { post -> post.processingStatus == ProcessingStatus.SUMMARY_READY.name }
                    ?.let { post -> DigestEligibleSummary(post = post, summary = summary) }
            }
            .sortedWith(
                compareByDescending<DigestEligibleSummary> { it.post.publishedAt ?: it.post.discoveredAt }
                    .thenByDescending { it.summary.createdAt }
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
                val remainingCount = items.size - index
                if (length + section.length > maxBodyChars) {
                    appendOmittedLineIfFits(remainingCount, maxBodyChars)
                    return@buildString
                }
                append(section)
            }
        }.trimEnd()
    }

    private fun String.cleanInline(): String = trim().replace(WHITESPACE, " ")

    private fun StringBuilder.appendOmittedLineIfFits(omittedCount: Int, maxBodyChars: Int) {
        if (omittedCount <= 0) return
        val line = "\n\n+ $omittedCount more update(s) omitted to keep this brief."
        if (length + line.length <= maxBodyChars) {
            append(line)
        }
    }

    companion object {
        const val NO_SUMMARIES_TEXT: String = "Airwallex FYI: No summarized updates are available yet."
        private const val DEFAULT_LIMIT = 3
        private const val MAX_LIMIT = 5
        private val WHITESPACE = Regex("\\s+")
    }
}
