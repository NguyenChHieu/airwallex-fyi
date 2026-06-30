package com.airwallexfyi.admin

import com.airwallexfyi.articles.ExtractedArticle
import com.airwallexfyi.articles.ExtractionSource
import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.digests.DigestDeliveryRecord
import com.airwallexfyi.digests.DigestDeliveryRepository
import com.airwallexfyi.monitor.MonitorRunService
import com.airwallexfyi.monitor.PostStateService
import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.posts.SourceType
import com.airwallexfyi.subscribers.SubscriberChannelRepository
import com.airwallexfyi.subscribers.SubscriberChannelType
import com.airwallexfyi.subscribers.SubscriberStatus
import com.airwallexfyi.summaries.ArticleSummaryResult
import com.airwallexfyi.summaries.ArticleSummaryService
import com.airwallexfyi.summaries.SummaryRecord
import com.airwallexfyi.summaries.SummaryRepository
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class AdminController(
    private val appProperties: AppProperties,
    private val postRepository: PostRepository,
    private val summaryRepository: SummaryRepository,
    private val subscriberChannelRepository: SubscriberChannelRepository,
    private val digestDeliveryRepository: DigestDeliveryRepository,
    private val postStateService: PostStateService,
    private val articleSummaryService: ArticleSummaryService,
    private val monitorRunService: MonitorRunService,
) {
    @GetMapping("/admin/health")
    fun health(): AdminHealthResponse = AdminHealthResponse(
        status = "ok",
        dryRun = appProperties.dryRun,
        schedulerEnabled = appProperties.scheduler.enabled,
    )

    @GetMapping("/admin/status")
    fun status(): AdminStatusResponse = AdminStatusResponse(
        status = "ok",
        dryRun = appProperties.dryRun,
        schedulerEnabled = appProperties.scheduler.enabled,
        subscribers = AdminSubscriberStatusResponse(
            telegramActive = activeSubscriberCount(SubscriberChannelType.TELEGRAM),
            whatsappActive = activeSubscriberCount(SubscriberChannelType.WHATSAPP),
        ),
        latestDigest = latestDigest()?.let(AdminLatestDigestResponse::from),
        latestRunHint = AdminLatestRunHintResponse(
            lastDiscoveredPost = latestDiscoveredPost()?.let(AdminPostHintResponse::from),
            lastSummary = latestSummary()?.let(AdminSummaryHintResponse::from),
        ),
    )

    @GetMapping("/admin/posts/recent")
    fun recentPosts(
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) sourceType: SourceType?,
        @RequestParam(required = false) processingStatus: ProcessingStatus?,
    ): AdminRecentPostsResponse {
        val posts = postRepository.findAll()
            .asSequence()
            .filter { post -> sourceType == null || post.sourceType.equals(sourceType.name, ignoreCase = true) }
            .filter { post -> processingStatus == null || post.processingStatus.equals(processingStatus.name, ignoreCase = true) }
            .sortedWith(
                compareByDescending<PostRecord> { it.discoveredAt }
                    .thenByDescending { it.publishedAt ?: Instant.EPOCH },
            )
            .take(limit.coerceIn(1, MAX_LIMIT))
            .map { post -> post.toAdminPostResponse() }
            .toList()

        return AdminRecentPostsResponse(
            count = posts.size,
            posts = posts,
        )
    }

    @PostMapping("/admin/run-once")
    fun runOnce(): AdminRunOnceResponse = AdminRunOnceResponse.from(monitorRunService.runOnce())

    @PostMapping("/admin/posts/{id}/summarize")
    fun summarizePost(@PathVariable id: UUID): AdminSummarizePostResponse {
        val post = postRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "post not found") }
        val existingSummary = summaryRepository.findByPostId(post.identifier())
        if (post.processingStatus != ProcessingStatus.APPROVAL_NEEDED.name && existingSummary != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "post already has a summary")
        }

        return when (val result = articleSummaryService.summarize(post, post.toExtractedArticle(), replaceExisting = true)) {
            is ArticleSummaryResult.Success -> {
                postStateService.updateProcessingStatus(post.identifier(), ProcessingStatus.SUMMARY_READY)
                AdminSummarizePostResponse(
                    postId = post.identifier(),
                    url = post.url,
                    status = ProcessingStatus.SUMMARY_READY.name,
                    headline = result.summary.headline,
                    tags = result.summary.tags,
                )
            }
            is ArticleSummaryResult.Failure -> {
                postStateService.updateProcessingStatus(post.identifier(), ProcessingStatus.SUMMARY_FAILED)
                AdminSummarizePostResponse(
                    postId = post.identifier(),
                    url = post.url,
                    status = ProcessingStatus.SUMMARY_FAILED.name,
                    failureReason = result.reason,
                )
            }
        }
    }

    private fun activeSubscriberCount(channel: String): Int =
        subscriberChannelRepository.findByChannelAndStatusOrderByCreatedAtAsc(channel, SubscriberStatus.ACTIVE).size

    private fun latestDigest(): DigestDeliveryRecord? = digestDeliveryRepository.findAll()
        .maxWithOrNull(
            compareBy<DigestDeliveryRecord> { it.attemptedAt }
                .thenBy { it.createdAt },
        )

    private fun latestDiscoveredPost(): PostRecord? = postRepository.findAll()
        .maxWithOrNull(
            compareBy<PostRecord> { it.discoveredAt }
                .thenBy { it.createdAt },
        )

    private fun latestSummary(): SummaryRecord? = summaryRepository.findAll()
        .maxWithOrNull(
            compareBy<SummaryRecord> { it.createdAt }
                .thenBy { it.updatedAt },
        )

    private fun PostRecord.toExtractedArticle(): ExtractedArticle = ExtractedArticle(
        url = url,
        sourceType = runCatching { SourceType.valueOf(sourceType.uppercase()) }
            .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported source type") },
        title = title ?: url.substringAfterLast('/').ifBlank { url },
        description = description,
        author = author,
        publishedAt = publishedAt,
        bodyText = articleBody?.takeIf { it.isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "post has no article body to summarize"),
        contentHash = contentHash.orEmpty(),
        extractionSource = ExtractionSource.HTML_FALLBACK,
    )

    private fun PostRecord.toAdminPostResponse(): AdminPostResponse = AdminPostResponse(
        id = identifier(),
        url = url,
        sourceType = sourceType,
        title = title,
        description = description,
        author = author,
        publishedAt = publishedAt,
        sitemapLastmod = sitemapLastmod,
        discoveredAt = discoveredAt,
        contentHash = contentHash,
        processingStatus = processingStatus,
        bodyPreview = articleBody.preview(),
    )

    private fun String?.preview(): String? {
        val normalized = this?.trim()?.replace(WHITESPACE, " ") ?: return null
        if (normalized.isBlank()) return null
        return if (normalized.length <= BODY_PREVIEW_LIMIT) {
            normalized
        } else {
            normalized.take(BODY_PREVIEW_LIMIT - 3) + "..."
        }
    }

    private companion object {
        const val MAX_LIMIT = 20
        const val BODY_PREVIEW_LIMIT = 240
        val WHITESPACE = Regex("\\s+")
    }
}
