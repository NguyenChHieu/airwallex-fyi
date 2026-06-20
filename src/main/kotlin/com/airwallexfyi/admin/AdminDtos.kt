package com.airwallexfyi.admin

import com.airwallexfyi.monitor.MonitorRunError
import com.airwallexfyi.monitor.MonitorRunResult
import com.airwallexfyi.monitor.MonitorRunSampleUrls
import java.time.Instant
import java.util.UUID

data class AdminHealthResponse(
    val status: String,
    val dryRun: Boolean,
    val schedulerEnabled: Boolean,
)

data class AdminRecentPostsResponse(
    val count: Int,
    val posts: List<AdminPostResponse>,
)

data class AdminPostResponse(
    val id: UUID,
    val url: String,
    val sourceType: String,
    val title: String?,
    val description: String?,
    val author: String?,
    val publishedAt: Instant?,
    val sitemapLastmod: Instant?,
    val discoveredAt: Instant,
    val contentHash: String?,
    val processingStatus: String,
    val bodyPreview: String?,
)

data class AdminRunOnceResponse(
    val status: String,
    val message: String,
    val sitemapFetched: Boolean,
    val discoveredCount: Int,
    val seededCount: Int,
    val newCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val sampleUrls: MonitorRunSampleUrls,
    val sampleErrors: List<MonitorRunError>,
    val externalCallsTriggered: Boolean,
) {
    companion object {
        fun from(result: MonitorRunResult): AdminRunOnceResponse = AdminRunOnceResponse(
            status = result.status,
            message = result.message,
            sitemapFetched = result.sitemapFetched,
            discoveredCount = result.discoveredCount,
            seededCount = result.seededCount,
            newCount = result.newCount,
            updatedCount = result.updatedCount,
            skippedCount = result.skippedCount,
            failedCount = result.failedCount,
            sampleUrls = result.sampleUrls,
            sampleErrors = result.sampleErrors,
            externalCallsTriggered = result.externalCallsTriggered,
        )
    }
}
