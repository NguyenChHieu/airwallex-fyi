package com.airwallexfyi.admin

import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.monitor.MonitorRunService
import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.posts.SourceType
import java.time.Instant
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class AdminController(
    private val appProperties: AppProperties,
    private val postRepository: PostRepository,
    private val monitorRunService: MonitorRunService,
) {
    @GetMapping("/admin/health")
    fun health(): AdminHealthResponse = AdminHealthResponse(
        status = "ok",
        dryRun = appProperties.dryRun,
        schedulerEnabled = appProperties.scheduler.enabled,
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
