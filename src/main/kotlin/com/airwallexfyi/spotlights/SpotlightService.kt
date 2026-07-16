package com.airwallexfyi.spotlights

import com.airwallexfyi.articles.ArticleExtractor
import com.airwallexfyi.articles.ArticleUnavailableException
import com.airwallexfyi.articles.ExtractedArticle
import com.airwallexfyi.articles.ExtractionSource
import com.airwallexfyi.monitor.PostStateService
import com.airwallexfyi.notifications.MessageBodyLimits
import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.posts.SourceType
import com.airwallexfyi.sources.SitemapEntry
import com.airwallexfyi.summaries.ArticleSummaryResult
import com.airwallexfyi.summaries.ArticleSummaryService
import com.airwallexfyi.summaries.StructuredSummary
import com.airwallexfyi.summaries.SummaryRecord
import com.airwallexfyi.summaries.SummaryRepository
import com.airwallexfyi.summaries.toStructuredSummary
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

@Service
class SpotlightService(
    private val postRepository: PostRepository,
    private val summaryRepository: SummaryRepository,
    private val articleExtractor: ArticleExtractor,
    private val articleSummaryService: ArticleSummaryService,
    private val postStateService: PostStateService,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun formatSpotlight(maxBodyChars: Int = MessageBodyLimits.TELEGRAM): String {
        val candidates = recentCandidates().shuffled()
        if (candidates.isEmpty()) return NO_POSTS_TEXT

        for (post in candidates) {
            try {
                return buildSpotlight(post, maxBodyChars)
            } catch (error: ArticleUnavailableException) {
                logger.info("Spotlight skipped unavailable article url={}: {}", post.url, error.boundedReason())
            } catch (error: RuntimeException) {
                logger.warn("Spotlight failed for url={}: {}", post.url, error.boundedReason())
                return failureMessage(post)
            }
        }

        return NO_POSTS_TEXT
    }

    private fun recentCandidates(): List<PostRecord> = postRepository.findAll()
        .asSequence()
        .filter { post -> post.sourceType in SUPPORTED_SOURCE_TYPES }
        .sortedWith(
            compareByDescending<PostRecord> { it.publishedAt ?: it.sitemapLastmod ?: it.discoveredAt }
                .thenBy { it.url },
        )
        .take(RECENT_POOL_SIZE)
        .toList()

    private fun buildSpotlight(post: PostRecord, maxBodyChars: Int): String {
        val existing = summaryRepository.findByPostId(post.identifier())
        currentSummary(post, existing)?.let { summary ->
            return formatMessage(post, summary, maxBodyChars)
        }

        val article = post.toStoredArticleOrNull() ?: articleExtractor.extract(post.toSitemapEntry())
        val hydratedPost = if (post.articleBody.isNullOrBlank()) {
            postStateService.hydrateExistingArticle(post.identifier(), article) ?: post
        } else {
            post
        }

        return when (
            val result = articleSummaryService.summarize(
                post = hydratedPost,
                article = article,
                replaceExisting = existing != null,
            )
        ) {
            is ArticleSummaryResult.Success -> {
                if (hydratedPost.processingStatus !in HISTORICAL_STATUSES) {
                    postStateService.updateProcessingStatus(hydratedPost.identifier(), ProcessingStatus.SUMMARY_READY)
                }
                formatMessage(hydratedPost, result.summary, maxBodyChars)
            }
            is ArticleSummaryResult.Failure -> {
                logger.warn("Spotlight summary failed for url={}: {}", hydratedPost.url, result.reason)
                failureMessage(hydratedPost)
            }
        }
    }

    private fun currentSummary(post: PostRecord, record: SummaryRecord?): StructuredSummary? {
        if (record == null || post.processingStatus !in CURRENT_SUMMARY_STATUSES) return null
        return runCatching { record.toStructuredSummary(objectMapper) }.getOrNull()
    }

    private fun PostRecord.toStoredArticleOrNull(): ExtractedArticle? {
        val body = articleBody?.takeIf { it.isNotBlank() } ?: return null
        return ExtractedArticle(
            url = url,
            sourceType = parsedSourceType(),
            title = title ?: fallbackTitle(),
            description = description,
            author = author,
            publishedAt = publishedAt,
            bodyText = body,
            contentHash = contentHash.orEmpty(),
            extractionSource = ExtractionSource.HTML_FALLBACK,
        )
    }

    private fun PostRecord.toSitemapEntry(): SitemapEntry = SitemapEntry(
        url = url,
        sourceType = parsedSourceType(),
        sitemapLastmod = sitemapLastmod,
        discoveredAt = discoveredAt,
    )

    private fun PostRecord.parsedSourceType(): SourceType = SourceType.valueOf(sourceType.uppercase())

    private fun PostRecord.fallbackTitle(): String = url.substringAfterLast('/').ifBlank { url }

    private fun formatMessage(post: PostRecord, summary: StructuredSummary, maxBodyChars: Int): String {
        val fullMessage = buildFullMessage(post, summary)
        if (fullMessage.length <= maxBodyChars) return fullMessage
        return buildBoundedMessage(post, summary, maxBodyChars)
    }

    private fun buildFullMessage(post: PostRecord, summary: StructuredSummary): String = buildString {
        appendLine("Airwallex FYI Spotlight")
        appendLine()
        appendLine("[${summary.sourceType.displayName()}] ${summary.headline.cleanInline()}")
        appendLine()
        summary.bullets.forEach { bullet -> appendLine("- ${bullet.cleanInline()}") }
        appendLine()
        appendLine("Why it matters:")
        appendLine(summary.whyItMatters.cleanInline())
        appendLine()
        append("Read: ${post.url}")
    }

    private fun buildBoundedMessage(post: PostRecord, summary: StructuredSummary, maxBodyChars: Int): String {
        val readLine = "Read: ${post.url}"
        val builder = StringBuilder()
            .appendLine("Airwallex FYI Spotlight")
            .appendLine()
            .appendLine("[${summary.sourceType.displayName()}] ${summary.headline.cleanInline().boundedInline(HEADLINE_LIMIT)}")
            .appendLine()

        val whySection = "\nWhy it matters:\n${summary.whyItMatters.cleanInline()}\n\n"
        for ((index, bullet) in summary.bullets.withIndex()) {
            val line = "- ${bullet.cleanInline()}\n"
            val remaining = summary.bullets.size - index
            val omittedLine = "+ $remaining more detail(s) omitted to fit Telegram.\n"
            if (builder.length + line.length + whySection.length + readLine.length > maxBodyChars) {
                if (builder.length + omittedLine.length + readLine.length <= maxBodyChars) {
                    builder.append(omittedLine)
                }
                break
            }
            builder.append(line)
        }

        if (builder.length + whySection.length + readLine.length <= maxBodyChars) {
            builder.append(whySection)
        } else {
            builder.appendLine()
        }
        builder.append(readLine)
        return builder.toString().take(maxBodyChars)
    }

    private fun failureMessage(post: PostRecord): String = buildString {
        appendLine("Airwallex FYI Spotlight")
        appendLine()
        appendLine("I couldn't summarize this post right now, but you can still read it here:")
        append(post.url)
    }

    private fun SourceType.displayName(): String = name.lowercase().replaceFirstChar(Char::uppercase)

    private fun String.cleanInline(): String = trim().replace(WHITESPACE, " ")

    private fun String.boundedInline(limit: Int): String =
        if (length <= limit) this else take(limit - ELLIPSIS.length).trimEnd() + ELLIPSIS

    private fun Throwable.boundedReason(): String =
        (message ?: javaClass.simpleName).lineSequence().firstOrNull()?.take(ERROR_LIMIT) ?: javaClass.simpleName

    companion object {
        const val NO_POSTS_TEXT: String = "Airwallex FYI: No Blog or Newsroom posts are available yet."
        private const val RECENT_POOL_SIZE = 25
        private const val HEADLINE_LIMIT = 240
        private const val ERROR_LIMIT = 240
        private const val ELLIPSIS = "..."
        private val WHITESPACE = Regex("\\s+")
        private val SUPPORTED_SOURCE_TYPES = SourceType.entries.map { it.name }.toSet()
        private val HISTORICAL_STATUSES = setOf(ProcessingStatus.SEEDED.name, ProcessingStatus.BASELINED.name)
        private val CURRENT_SUMMARY_STATUSES = setOf(
            ProcessingStatus.SEEDED.name,
            ProcessingStatus.BASELINED.name,
            ProcessingStatus.SUMMARY_READY.name,
            ProcessingStatus.ALERT_SENT.name,
            ProcessingStatus.DRY_RUN_READY.name,
        )
    }
}
