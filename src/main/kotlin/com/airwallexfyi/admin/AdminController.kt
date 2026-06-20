package com.airwallexfyi.admin

import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.monitor.MonitorRunService
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.posts.SourceType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin")
class AdminController(
    private val appProperties: AppProperties,
    private val adminPostQueryService: AdminPostQueryService,
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
        @RequestParam(required = false) sourceType: SourceType?,
        @RequestParam(required = false) processingStatus: ProcessingStatus?,
    ): AdminRecentPostsResponse {
        val posts = adminPostQueryService.recentPosts(
            limit = limit,
            sourceType = sourceType,
            processingStatus = processingStatus,
        )

        return AdminRecentPostsResponse(
            count = posts.size,
            posts = posts,
        )
    }

    @PostMapping("/run-once")
    fun runOnce(): AdminRunOnceResponse = AdminRunOnceResponse.from(monitorRunService.runOnce())
}
