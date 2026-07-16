package com.airwallexfyi.monitor

import com.airwallexfyi.articles.ArticleExtractor
import com.airwallexfyi.articles.ArticleUnavailableException
import com.airwallexfyi.articles.ExtractedArticle
import com.airwallexfyi.digests.DailyDigestRunResult
import com.airwallexfyi.digests.DailyDigestService
import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.sources.AirwallexSourceDiscoveryService
import com.airwallexfyi.subscribers.SubscriberSeedService
import com.airwallexfyi.subscribers.TelegramSubscriptionService
import com.airwallexfyi.subscribers.TelegramSubscriptionSyncResult
import com.airwallexfyi.summaries.ArticleSummaryResult
import com.airwallexfyi.summaries.ArticleSummaryService
import com.airwallexfyi.summaries.SummaryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MonitorRunService(
    private val sourceDiscoveryService: AirwallexSourceDiscoveryService,
    private val articleExtractor: ArticleExtractor,
    private val postStateService: PostStateService,
    private val summaryRepository: SummaryRepository,
    private val articleSummaryService: ArticleSummaryService,
    private val subscriberSeedService: SubscriberSeedService,
    private val telegramSubscriptionService: TelegramSubscriptionService,
    private val dailyDigestService: DailyDigestService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun runOnce(): MonitorRunResult {
        logger.info("Monitor run started.")
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
        logger.info("Monitor discovery completed: candidates={}", candidates.size)

        val statePlan = postStateService.planWork(candidates)
        logger.info(
            "Monitor work planned: workItems={} skipped={} seed={} baseline={} new={} updateCheck={}",
            statePlan.workItems.size,
            statePlan.skippedCount,
            statePlan.workItems.count { it.mode == PostWorkMode.SEED },
            statePlan.workItems.count { it.mode == PostWorkMode.BASELINE },
            statePlan.workItems.count { it.mode == PostWorkMode.NEW },
            statePlan.workItems.count { it.mode == PostWorkMode.UPDATE_CHECK },
        )
        val accumulator = MonitorRunAccumulator(
            discoveredCount = candidates.size,
            skippedCount = statePlan.skippedCount,
        )

        statePlan.workItems.forEachIndexed { index, workItem ->
            try {
                if (shouldLogProgress(index, statePlan.workItems.size)) {
                    logger.info(
                        "Monitor processing item {}/{} mode={} url={}",
                        index + 1,
                        statePlan.workItems.size,
                        workItem.mode,
                        workItem.entry.url,
                    )
                }
                if (workItem.mode == PostWorkMode.BASELINE) {
                    accumulator.record(postStateService.baseline(workItem))
                    return@forEachIndexed
                }
                val article = articleExtractor.extract(workItem.entry)
                val applyResult = postStateService.apply(workItem, article)
                accumulator.record(applyResult)
                when (applyResult.kind) {
                    PostApplyKind.NEW -> applyResult.post?.let { handleNewPost(it, article, accumulator) }
                    PostApplyKind.UPDATED -> {
                        applyResult.post?.let { postStateService.updateProcessingStatus(it.identifier(), ProcessingStatus.APPROVAL_NEEDED) }
                        accumulator.recordApprovalNeeded(applyResult.url, "content_changed")
                    }
                    PostApplyKind.SKIPPED -> applyResult.post?.let { recordMissingSummaryApproval(it, accumulator) }
                    PostApplyKind.SEEDED,
                    PostApplyKind.BASELINED -> Unit
                }
            } catch (ex: ArticleUnavailableException) {
                logger.info("Monitor skipped unavailable article url={} reason={}", workItem.entry.url, ex.shortReason())
                accumulator.record(postStateService.markArticleUnavailable(workItem))
            } catch (ex: RuntimeException) {
                accumulator.recordFailure(workItem.entry.url, ex.shortReason())
            }
        }

        logger.info("Monitor article processing completed.")
        logger.info("Monitor digest phase started.")
        runDailyDigest(accumulator)
        logger.info("Monitor run finished.")

        return accumulator.toResult()
    }

    private fun shouldLogProgress(index: Int, total: Int): Boolean =
        index == 0 || index + 1 == total || index % PROGRESS_LOG_INTERVAL == 0

    private fun handleNewPost(post: PostRecord, article: ExtractedArticle, accumulator: MonitorRunAccumulator) {
        when (val summaryResult = articleSummaryService.summarize(post, article)) {
            is ArticleSummaryResult.Success -> {
                postStateService.updateProcessingStatus(post.identifier(), ProcessingStatus.SUMMARY_READY)
                accumulator.recordSummarySuccess()
            }
            is ArticleSummaryResult.Failure -> {
                postStateService.updateProcessingStatus(post.identifier(), ProcessingStatus.SUMMARY_FAILED)
                accumulator.recordSummaryFailure(post.url, summaryResult.reason)
            }
        }
    }

    private fun runDailyDigest(accumulator: MonitorRunAccumulator) {
        try {
            subscriberSeedService.seedDefaultSubscriberIfConfigured()
            logger.info("Telegram subscription sync started.")
            accumulator.recordTelegramSubscriptionResult(telegramSubscriptionService.syncSubscriptions())
            logger.info("Daily digest send started.")
            accumulator.recordDigestResult(dailyDigestService.sendDailyDigests())
        } catch (ex: RuntimeException) {
            accumulator.recordDigestFailure("Digest delivery failed: ${ex.shortReason()}")
        }
    }

    private fun recordMissingSummaryApproval(post: PostRecord, accumulator: MonitorRunAccumulator) {
        if (post.processingStatus in MISSING_SUMMARY_APPROVAL_SKIP_STATUSES) return

        val alreadySummarized = summaryRepository.findByPostId(post.identifier()) != null
        if (alreadySummarized) return

        postStateService.updateProcessingStatus(post.identifier(), ProcessingStatus.APPROVAL_NEEDED)
        accumulator.recordApprovalNeeded(post.url, "missing_summary")
    }
}

private class MonitorRunAccumulator(
    private val discoveredCount: Int,
    private var skippedCount: Int,
) {
    private var seededCount = 0
    private var baselinedCount = 0
    private var newCount = 0
    private var updatedCount = 0
    private var failedCount = 0
    private var summarizedCount = 0
    private var summaryFailedCount = 0
    private var digestSentCount = 0
    private var digestNoChangeCount = 0
    private var digestSkippedDuplicateCount = 0
    private var digestSkippedAccessCount = 0
    private var digestFailedCount = 0
    private var twilioCallsTriggered = false
    private val seededUrls = mutableListOf<String>()
    private val baselinedUrls = mutableListOf<String>()
    private val newUrls = mutableListOf<String>()
    private val updatedUrls = mutableListOf<String>()
    private val errors = mutableListOf<MonitorRunError>()
    private val payloads = mutableListOf<String>()
    private val approvalNeeded = mutableListOf<MonitorApprovalNeeded>()
    private val approvalUrls = mutableSetOf<String>()
    private val digestDeliveries = mutableListOf<String>()
    private val digestErrors = mutableListOf<String>()

    val approvalNeededCount: Int
        get() = approvalUrls.size

    fun record(result: PostApplyResult) {
        when (result.kind) {
            PostApplyKind.SEEDED -> {
                seededCount += 1
                seededUrls.addSample(result.url)
            }
            PostApplyKind.BASELINED -> {
                baselinedCount += 1
                baselinedUrls.addSample(result.url)
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

    fun recordDigestResult(result: DailyDigestRunResult) {
        digestSentCount += result.digestSentCount
        digestNoChangeCount += result.noChangeCount
        digestSkippedDuplicateCount += result.skippedDuplicateCount
        digestSkippedAccessCount += result.skippedAccessCount
        digestFailedCount += result.failedCount
        twilioCallsTriggered = twilioCallsTriggered || result.twilioCallsTriggered
        result.samplePayloads.forEach { addPayload(it) }
        result.sampleDeliveries.forEach { digestDeliveries.addSample(it) }
        result.sampleErrors.forEach { digestErrors.addSample(it) }
    }

    fun recordTelegramSubscriptionResult(result: TelegramSubscriptionSyncResult) {
        if (result.skipped) return
        if (result.processedCount > 0 || result.subscribedCount > 0 || result.unsubscribedCount > 0) {
            digestDeliveries.addSample(
                "telegram subscriptions processed=${result.processedCount} subscribed=${result.subscribedCount} unsubscribed=${result.unsubscribedCount}",
            )
        }
        digestFailedCount += result.failedCount
        result.sampleErrors.forEach { digestErrors.addSample(it) }
    }

    fun recordDigestFailure(reason: String) {
        digestFailedCount += 1
        digestErrors.addSample(reason)
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
        val failureTotal = failedCount + summaryFailedCount + digestFailedCount
        val status = when {
            failureTotal == 0 -> MonitorRunStatus.COMPLETED
            seededCount + baselinedCount + newCount + updatedCount + skippedCount + summarizedCount + approvalNeededCount + digestSentCount + digestNoChangeCount + digestSkippedDuplicateCount + digestSkippedAccessCount > 0 -> MonitorRunStatus.PARTIAL_FAILURE
            else -> MonitorRunStatus.FAILED
        }
        val message = when (status) {
            MonitorRunStatus.COMPLETED -> "Monitor run completed."
            MonitorRunStatus.PARTIAL_FAILURE -> "Monitor run completed with article or delivery failures."
            else -> "Monitor run failed before any article was stored or skipped."
        }

        return MonitorRunResult(
            status = status,
            sitemapFetched = true,
            discoveredCount = discoveredCount,
            seededCount = seededCount,
            baselinedCount = baselinedCount,
            newCount = newCount,
            updatedCount = updatedCount,
            skippedCount = skippedCount,
            failedCount = failedCount,
            summarizedCount = summarizedCount,
            summaryFailedCount = summaryFailedCount,
            approvalNeededCount = approvalNeededCount,
            digestSentCount = digestSentCount,
            digestNoChangeCount = digestNoChangeCount,
            digestSkippedDuplicateCount = digestSkippedDuplicateCount,
            digestSkippedAccessCount = digestSkippedAccessCount,
            digestFailedCount = digestFailedCount,
            sampleUrls = MonitorRunSampleUrls(
                seeded = seededUrls,
                baselined = baselinedUrls,
                new = newUrls,
                updated = updatedUrls,
            ),
            sampleErrors = errors,
            samplePayloads = payloads,
            sampleApprovalNeeded = approvalNeeded,
            sampleDigestDeliveries = digestDeliveries,
            sampleDigestErrors = digestErrors,
            externalCallsTriggered = summarizedCount > 0 || twilioCallsTriggered,
            twilioCallsTriggered = twilioCallsTriggered,
            message = message,
        )
    }

    private fun addPayload(payload: String) {
        if (payloads.size < SAMPLE_LIMIT) payloads += payload
    }

    private fun MutableList<String>.addSample(value: String) {
        if (size < SAMPLE_LIMIT) add(value)
    }
}

private fun Throwable.shortReason(): String =
    (message ?: javaClass.simpleName)
        .lineSequence()
        .firstOrNull()
        ?.take(240)
        ?: javaClass.simpleName

private const val SAMPLE_LIMIT = 5
private const val PROGRESS_LOG_INTERVAL = 50
private val MISSING_SUMMARY_APPROVAL_SKIP_STATUSES = setOf(
    ProcessingStatus.SEEDED.name,
    ProcessingStatus.BASELINED.name,
    ProcessingStatus.SUMMARY_READY.name,
    ProcessingStatus.ALERT_SENT.name,
    ProcessingStatus.DRY_RUN_READY.name,
    ProcessingStatus.SUMMARY_FAILED.name,
    ProcessingStatus.ALERT_FAILED.name,
    ProcessingStatus.APPROVAL_NEEDED.name,
)

