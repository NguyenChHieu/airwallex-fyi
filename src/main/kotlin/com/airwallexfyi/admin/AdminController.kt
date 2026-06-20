package com.airwallexfyi.admin

import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.monitor.MonitorRunService
import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin")
class AdminController(
    private val appProperties: AppProperties,
    private val postRepository: PostRepository,
    private val monitorRunService: MonitorRunService,
) {
    @GetMapping("/health")
    fun health(): AdminHealthResponse = AdminHealthResponse(
        status = "ok",
        dryRun = appProperties.dryRun,
        schedulerEnabled = appProperties.scheduler.enabled,
    )

    @GetMapping("/posts/recent")
    fun recentPosts(
        @RequestParam(defaultValue = "20") limit: Int,
    ): AdminRecentPostsResponse {
        val posts = postRepository.findTop20ByOrderByDiscoveredAtDesc()
            .take(limit.coerceIn(1, 20))
            .map { it.toAdminPostResponse() }

        return AdminRecentPostsResponse(
            count = posts.size,
            posts = posts,
        )
    }

    @PostMapping("/run-once")
    fun runOnce(): AdminRunOnceResponse {
        val result = monitorRunService.runOnce()
        return AdminRunOnceResponse(
            status = result.status,
            message = result.message,
            externalCallsTriggered = result.externalCallsTriggered,
        )
    }

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
    )
}