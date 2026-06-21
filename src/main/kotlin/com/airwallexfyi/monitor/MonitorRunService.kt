package com.airwallexfyi.monitor

import com.airwallexfyi.articles.ArticleExtractor
import com.airwallexfyi.articles.ExtractedArticle
import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.notifications.NotificationResult
import com.airwallexfyi.notifications.NotificationStatus
import com.airwallexfyi.notifications.WhatsAppAlertFormatter
import com.airwallexfyi.notifications.WhatsAppNotifier
import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.sources.AirwallexSourceDiscoveryService
import com.airwallexfyi.sources.SitemapEntry
import com.airwallexfyi.summaries.ArticleSummaryResult
import com.airwallexfyi.summaries.ArticleSummaryService
import com.airwallexfyi.summaries.SummaryRepository
import org.springframework.stereotype.Service

@Service
class MonitorRunService(
    private val sourceDiscoveryService: AirwallexSourceDiscoveryService,
    private val articleExtractor: ArticleExtractor,
    private val postStateService: PostStateService,
    private val postRepository: PostRepository,
    private val summaryRepository: SummaryRepository,
    private val articleSummaryService: ArticleSummaryService,
    private val alertFormatter: WhatsAppAlertFormatter,
    private val whatsAppNotifier: WhatsAppNotifier,
    private val properties: AppProperties,
) {
    fun runOnce(): MonitorRunResult {
        val candidates = try {
            sourceDiscoveryService.discoverCandidates()
        } catch (ex: RuntimeException) {
            val reason = ex.shortReason()
            return MonitorRunResult(
                status = MonitorRunStatus.FAILED,
                sitemapFetched = false,
                discoveredCount = 0,
                seededCount = 0,
                newCount = 0,
                updatedCount = 0,
                skippedCount = 0,
                failedCount = 1,
                sampleErrors = listOf(MonitorRunError(url = null, reason = "Sitemap discovery failed: $reason")),
                externalCallsTriggered = false,
                twilioCallsTriggered = false,
                message = "Sitemap discovery failed; no posts were written.",
            )
        }

        val statePlan = postStateService.planWork(candidates)
        val accumulator = MonitorRunAccumulator(
            discoveredCount = candidates.size,
            skippedCount = statePlan.skippedCount,
        )

        statePlan.workItems.forEach { workItem ->
            try {
                val article = articleExtractor.extract(workItem.entry)
                val applyResult = postStateService.apply(workItem, article)
                accumulator.record(applyResult)
                when (applyResult.kind) {
                    PostApplyKind.NEW -> applyResult.post?.let { handleNewPost(it, article, accumulator) }
                    PostApplyKind.UPDATED -> {
                        applyResult.post?.let { postStateService.updateProcessingStatus(it.identifier(), ProcessingStatus.APPROVAL_NEEDED) }
                        accumulator.recordApprovalNeeded(applyResult.url, "content_changed")
                    }
                    PostApplyKind.SEEDED,
                    PostApplyKind.SKIPPED -> Unit
                }
            } catch (ex: RuntimeException) {
                accumulator.recordFailure(workItem.entry.url, ex.shortReason())
            }
        }

        recordMissingSummaryApprovals(candidates, accumulator)

        return accumulator.toResult()
    }

    private fun handleNewPost(post: PostRecord, article: ExtractedArticle, accumulator: MonitorRunAccumulator) {
        when (val summaryResult = articleSummaryService.summarize(post, article)) {
            is ArticleSummaryResult.Success -> {
                accumulator.recordSummarySuccess()
                val payload = alertFormatter.format(post, summaryResult.summary, properties.whatsapp.to)
                val notification = runCatching { whatsAppNotifier.send(post, payload) }
                    .getOrElse { ex ->
                        NotificationResult(
                            status = NotificationStatus.FAILED,
                            payloadPreview = payload.preview,
                            errorMessage = ex.shortReason(),
                            twilioCalled = false,
                        )
                    }
                when (notification.status) {
                    NotificationStatus.DRY_RUN -> {
                        postStateService.updateProcessingStatus(post.identifier(), ProcessingStatus.DRY_RUN_READY)
                        accumulator.recordDryRunAlert(notification)
                    }
                    NotificationStatus.SENT -> {
                        postStateService.updateProcessingStatus(post.identifier(), ProcessingStatus.ALERT_SENT)
                        accumulator.recordSentAlert(notification)
                    }
                    NotificationStatus.FAILED -> {
                        postStateService.updateProcessingStatus(post.identifier(), ProcessingStatus.ALERT_FAILED)
                        accumulator.recordAlertFailure(post.url, notification)
                    }
                }
            }
            is ArticleSummaryResult.Failure -> {
                postStateService.updateProcessingStatus(post.identifier(), ProcessingStatus.SUMMARY_FAILED)
                accumulator.recordSummaryFailure(post.url, summaryResult.reason)
            }
        }
    }

    private fun recordMissingSummaryApprovals(candidates: List<SitemapEntry>, accumulator: MonitorRunAccumulator) {
        candidates.forEach { candidate ->
            val post = postRepository.findByUrl(candidate.url) ?: return@forEach
            val alreadySummarized = summaryRepository.findByPostId(post.identifier()) != null
            if (!alreadySummarized && post.processingStatus != ProcessingStatus.SUMMARY_FAILED.name) {
                postStateService.updateProcessingStatus(post.identifier(), ProcessingStatus.APPROVAL_NEEDED)
                accumulator.recordApprovalNeeded(post.url, "missing_summary")
            }
        }
    }
}

private class MonitorRunAccumulator(
    private val discoveredCount: Int,
    private var skippedCount: Int,
) {
    private var seededCount = 0
    private var newCount = 0
    private var updatedCount = 0
    private var failedCount = 0
    private var summarizedCount = 0
    private var summaryFailedCount = 0
    private var alertSentCount = 0
    private var dryRunAlertCount = 0
    private var alertFailedCount = 0
    private var twilioCallsTriggered = false
    private val seededUrls = mutableListOf<String>()
    private val newUrls = mutableListOf<String>()
    private val updatedUrls = mutableListOf<String>()
    private val errors = mutableListOf<MonitorRunError>()
    private val payloads = mutableListOf<String>()
    private val approvalNeeded = mutableListOf<MonitorApprovalNeeded>()
    private val approvalUrls = mutableSetOf<String>()

    val approvalNeededCount: Int
        get() = approvalUrls.size

    fun record(result: PostApplyResult) {
        when (result.kind) {
            PostApplyKind.SEEDED -> {
                seededCount += 1
                seededUrls.addSample(result.url)
            }
            PostApplyKind.NEW -> {
                newCount += 1
                newUrls.addSample(result.url)
            }
            PostApplyKind.UPDATED -> {
                updatedCount += 1
                updatedUrls.addSample(result.url)
            }
            PostApplyKind.SKIPPED -> skippedCount += 1
        }
    }

    fun recordSummarySuccess() {
        summarizedCount += 1
    }

    fun recordSummaryFailure(url: String, reason: String) {
        summaryFailedCount += 1
        recordFailure(url, "Summary failed: $reason")
    }

    fun recordDryRunAlert(notification: NotificationResult) {
        dryRunAlertCount += 1
        addPayload(notification.payloadPreview)
    }

    fun recordSentAlert(notification: NotificationResult) {
        alertSentCount += 1
        twilioCallsTriggered = twilioCallsTriggered || notification.twilioCalled
        addPayload(notification.payloadPreview)
    }

    fun recordAlertFailure(url: String, notification: NotificationResult) {
        alertFailedCount += 1
        twilioCallsTriggered = twilioCallsTriggered || notification.twilioCalled
        recordFailure(url, "Alert failed: ${notification.errorMessage ?: "unknown error"}")
    }

    fun recordApprovalNeeded(url: String, reason: String) {
        if (!approvalUrls.add(url)) return
        if (approvalNeeded.size < SAMPLE_LIMIT) {
            approvalNeeded += MonitorApprovalNeeded(url = url, reason = reason)
        }
    }

    fun recordFailure(url: String, reason: String) {
        failedCount += 1
        if (errors.size < SAMPLE_LIMIT) {
            errors += MonitorRunError(url = url, reason = reason)
        }
    }

    fun toResult(): MonitorRunResult {
        val failureTotal = failedCount + summaryFailedCount + alertFailedCount
        val status = when {
            failureTotal == 0 -> MonitorRunStatus.COMPLETED
            seededCount + newCount + updatedCount + skippedCount + summarizedCount + approvalNeededCount > 0 -> MonitorRunStatus.PARTIAL_FAILURE
            else -> MonitorRunStatus.FAILED
        }
        val message = when (status) {
            MonitorRunStatus.COMPLETED -> "Monitor run completed."
            MonitorRunStatus.PARTIAL_FAILURE -> "Monitor run completed with article-level failures."
            else -> "Monitor run failed before any article was stored or skipped."
        }

        return MonitorRunResult(
            status = status,
            sitemapFetched = true,
            discoveredCount = discoveredCount,
            seededCount = seededCount,
            newCount = newCount,
            updatedCount = updatedCount,
            skippedCount = skippedCount,
            failedCount = failedCount,
            summarizedCount = summarizedCount,
            summaryFailedCount = summaryFailedCount,
            approvalNeededCount = approvalNeededCount,
            alertSentCount = alertSentCount,
            dryRunAlertCount = dryRunAlertCount,
            alertFailedCount = alertFailedCount,
            sampleUrls = MonitorRunSampleUrls(
                seeded = seededUrls,
                new = newUrls,
                updated = updatedUrls,
            ),
            sampleErrors = errors,
            samplePayloads = payloads,
            sampleApprovalNeeded = approvalNeeded,
            externalCallsTriggered = summarizedCount > 0 || twilioCallsTriggered,
            twilioCallsTriggered = twilioCallsTriggered,
            message = message,
        )
    }

    private fun addPayload(payload: String) {
        if (payloads.size < SAMPLE_LIMIT) payloads += payload
    }

    private fun MutableList<String>.addSample(url: String) {
        if (size < SAMPLE_LIMIT) add(url)
    }
}

private fun Throwable.shortReason(): String =
    (message ?: javaClass.simpleName)
        .lineSequence()
        .firstOrNull()
        ?.take(240)
        ?: javaClass.simpleName

private const val SAMPLE_LIMIT = 5
