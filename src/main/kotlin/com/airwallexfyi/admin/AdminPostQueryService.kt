package com.airwallexfyi.admin

import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.posts.SourceType
import java.time.Instant
import org.springframework.stereotype.Service

@Service
class AdminPostQueryService(
    private val postRepository: PostRepository,
) {
    fun recentPosts(
        limit: Int,
        sourceType: SourceType?,
        processingStatus: ProcessingStatus?,
    ): List<AdminPostResponse> = postRepository.findAll()
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
