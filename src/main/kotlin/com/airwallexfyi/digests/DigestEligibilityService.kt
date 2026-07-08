package com.airwallexfyi.digests

import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.summaries.SummaryRecord
import com.airwallexfyi.summaries.SummaryRepository
import java.time.Instant
import org.springframework.stereotype.Service

@Service
class DigestEligibilityService(
    private val summaryRepository: SummaryRepository,
    private val postRepository: PostRepository,
) {
    fun findEligibleSummariesSince(since: Instant?): List<DigestEligibleSummary> {
        val postsById = postRepository.findAll().associateBy { it.identifier() }
        return summaryRepository.findAll()
            .asSequence()
            .filter { summary -> since == null || summary.createdAt.isAfter(since) }
            .mapNotNull { summary ->
                postsById[summary.postId]
                    ?.let { post -> DigestEligibleSummary(post = post, summary = summary) }
            }
            .filter { candidate -> candidate.post.processingStatus == ProcessingStatus.SUMMARY_READY.name }
            .sortedWith(
                compareBy<DigestEligibleSummary> { it.summary.createdAt }
                    .thenBy { it.post.discoveredAt }
                    .thenBy { it.post.url },
            )
            .toList()
    }
}

data class DigestEligibleSummary(
    val post: PostRecord,
    val summary: SummaryRecord,
)
