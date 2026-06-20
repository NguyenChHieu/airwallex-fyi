package com.airwallexfyi.admin

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
)

data class AdminRunOnceResponse(
    val status: String,
    val message: String,
    val externalCallsTriggered: Boolean,
)