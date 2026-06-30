package com.airwallexfyi.admin

import com.airwallexfyi.digests.DigestDeliveryRecord
import com.airwallexfyi.monitor.MonitorApprovalNeeded
import com.airwallexfyi.monitor.MonitorRunError
import com.airwallexfyi.monitor.MonitorRunResult
import com.airwallexfyi.monitor.MonitorRunSampleUrls
import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.summaries.SummaryRecord
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class AdminHealthResponse(
    val status: String,
    val dryRun: Boolean,
    val schedulerEnabled: Boolean,
)

data class AdminStatusResponse(
    val status: String,
    val dryRun: Boolean,
    val schedulerEnabled: Boolean,
    val subscribers: AdminSubscriberStatusResponse,
    val latestDigest: AdminLatestDigestResponse?,
    val latestRunHint: AdminLatestRunHintResponse,
)

data class AdminSubscriberStatusResponse(
    val telegramActive: Int,
    val whatsappActive: Int,
)

data class AdminLatestDigestResponse(
    val localDate: LocalDate,
    val channel: String,
    val recipient: String,
    val messageType: String,
    val status: String,
    val attemptedAt: Instant,
    val sentAt: Instant?,
    val errorMessage: String?,
) {
    companion object {
        fun from(delivery: DigestDeliveryRecord): AdminLatestDigestResponse = AdminLatestDigestResponse(
            localDate = delivery.localDate,
            channel = delivery.channel,
            recipient = delivery.recipient,
            messageType = delivery.messageType,
            status = delivery.status,
            attemptedAt = delivery.attemptedAt,
            sentAt = delivery.sentAt,
            errorMessage = delivery.errorMessage,
        )
    }
}

data class AdminLatestRunHintResponse(
    val lastDiscoveredPost: AdminPostHintResponse?,
    val lastSummary: AdminSummaryHintResponse?,
)

data class AdminPostHintResponse(
    val url: String,
    val sourceType: String,
    val processingStatus: String,
    val discoveredAt: Instant,
    val publishedAt: Instant?,
) {
    companion object {
        fun from(post: PostRecord): AdminPostHintResponse = AdminPostHintResponse(
            url = post.url,
            sourceType = post.sourceType,
            processingStatus = post.processingStatus,
            discoveredAt = post.discoveredAt,
            publishedAt = post.publishedAt,
        )
    }
}

data class AdminSummaryHintResponse(
    val postId: UUID,
    val headline: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(summary: SummaryRecord): AdminSummaryHintResponse = AdminSummaryHintResponse(
            postId = summary.postId,
            headline = summary.headline,
            createdAt = summary.createdAt,
        )
    }
}

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
    val baselinedCount: Int,
    val newCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val summarizedCount: Int,
    val summaryFailedCount: Int,
    val approvalNeededCount: Int,
    val digestSentCount: Int,
    val digestNoChangeCount: Int,
    val digestSkippedDuplicateCount: Int,
    val digestFailedCount: Int,
    val sampleUrls: MonitorRunSampleUrls,
    val sampleErrors: List<MonitorRunError>,
    val samplePayloads: List<String>,
    val sampleApprovalNeeded: List<MonitorApprovalNeeded>,
    val sampleDigestDeliveries: List<String>,
    val sampleDigestErrors: List<String>,
    val externalCallsTriggered: Boolean,
    val twilioCallsTriggered: Boolean,
) {
    companion object {
        fun from(result: MonitorRunResult): AdminRunOnceResponse = AdminRunOnceResponse(
            status = result.status,
            message = result.message,
            sitemapFetched = result.sitemapFetched,
            discoveredCount = result.discoveredCount,
            seededCount = result.seededCount,
            baselinedCount = result.baselinedCount,
            newCount = result.newCount,
            updatedCount = result.updatedCount,
            skippedCount = result.skippedCount,
            failedCount = result.failedCount,
            summarizedCount = result.summarizedCount,
            summaryFailedCount = result.summaryFailedCount,
            approvalNeededCount = result.approvalNeededCount,
            digestSentCount = result.digestSentCount,
            digestNoChangeCount = result.digestNoChangeCount,
            digestSkippedDuplicateCount = result.digestSkippedDuplicateCount,
            digestFailedCount = result.digestFailedCount,
            sampleUrls = result.sampleUrls,
            sampleErrors = result.sampleErrors,
            samplePayloads = result.samplePayloads,
            sampleApprovalNeeded = result.sampleApprovalNeeded,
            sampleDigestDeliveries = result.sampleDigestDeliveries,
            sampleDigestErrors = result.sampleDigestErrors,
            externalCallsTriggered = result.externalCallsTriggered,
            twilioCallsTriggered = result.twilioCallsTriggered,
        )
    }
}

data class AdminSummarizePostResponse(
    val postId: UUID,
    val url: String,
    val status: String,
    val headline: String? = null,
    val tags: List<String> = emptyList(),
    val failureReason: String? = null,
)
