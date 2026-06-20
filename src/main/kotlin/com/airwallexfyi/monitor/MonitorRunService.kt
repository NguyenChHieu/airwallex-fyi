package com.airwallexfyi.monitor

import com.airwallexfyi.articles.ArticleExtractor
import com.airwallexfyi.sources.AirwallexSourceDiscoveryService
import org.springframework.stereotype.Service

@Service
class MonitorRunService(
    private val sourceDiscoveryService: AirwallexSourceDiscoveryService,
    private val articleExtractor: ArticleExtractor,
    private val postStateService: PostStateService,
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
                accumulator.record(postStateService.apply(workItem, article))
            } catch (ex: RuntimeException) {
                accumulator.recordFailure(workItem.entry.url, ex.shortReason())
            }
        }

        return accumulator.toResult()
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
    private val seededUrls = mutableListOf<String>()
    private val newUrls = mutableListOf<String>()
    private val updatedUrls = mutableListOf<String>()
    private val errors = mutableListOf<MonitorRunError>()

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

    fun recordFailure(url: String, reason: String) {
        failedCount += 1
        if (errors.size < SAMPLE_LIMIT) {
            errors += MonitorRunError(url = url, reason = reason)
        }
    }

    fun toResult(): MonitorRunResult {
        val status = when {
            failedCount == 0 -> MonitorRunStatus.COMPLETED
            seededCount + newCount + updatedCount + skippedCount > 0 -> MonitorRunStatus.PARTIAL_FAILURE
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
            sampleUrls = MonitorRunSampleUrls(
                seeded = seededUrls,
                new = newUrls,
                updated = updatedUrls,
            ),
            sampleErrors = errors,
            externalCallsTriggered = false,
            message = message,
        )
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