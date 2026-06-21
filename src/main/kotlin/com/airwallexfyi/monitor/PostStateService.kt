package com.airwallexfyi.monitor

import com.airwallexfyi.articles.ExtractedArticle
import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.sources.SitemapEntry
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

@Service
class PostStateService(
    private val postRepository: PostRepository,
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val properties: AppProperties,
) {
    fun planWork(candidates: List<SitemapEntry>): PostStatePlan {
        if (candidates.isEmpty()) return PostStatePlan(emptyList(), skippedCount = 0)

        val existingByUrl = postRepository.findAll().associateBy { it.url }
        if (existingByUrl.isEmpty()) {
            val seedItems = candidates
                .sortedWith(compareByDescending<SitemapEntry> { it.sitemapLastmod ?: Instant.EPOCH }.thenBy { it.url })
                .take(properties.source.firstRunSeedLimit)
                .map { PostWorkItem(it, PostWorkMode.SEED) }
            return PostStatePlan(
                workItems = seedItems,
                skippedCount = candidates.size - seedItems.size,
            )
        }

        var skippedCount = 0
        val workItems = candidates.mapNotNull { candidate ->
            val existing = existingByUrl[candidate.url]
            when {
                existing == null -> PostWorkItem(candidate, PostWorkMode.NEW)
                shouldCheckForUpdate(existing, candidate) -> PostWorkItem(candidate, PostWorkMode.UPDATE_CHECK)
                else -> {
                    skippedCount += 1
                    null
                }
            }
        }

        return PostStatePlan(workItems = workItems, skippedCount = skippedCount)
    }

    fun apply(workItem: PostWorkItem, article: ExtractedArticle): PostApplyResult = when (workItem.mode) {
        PostWorkMode.SEED -> {
            val post = insertArticle(workItem, article, processingStatus = ProcessingStatus.SEEDED.name)
            PostApplyResult(PostApplyKind.SEEDED, article.url, post)
        }
        PostWorkMode.NEW -> {
            val post = insertArticle(workItem, article, processingStatus = ProcessingStatus.DISCOVERED.name)
            PostApplyResult(PostApplyKind.NEW, article.url, post)
        }
        PostWorkMode.UPDATE_CHECK -> updateKnownArticle(workItem, article)
    }

    fun updateProcessingStatus(postId: UUID, status: ProcessingStatus): PostRecord? {
        val params = MapSqlParameterSource()
            .addValue("id", postId)
            .addValue("processingStatus", status.name)
            .addValue("updatedAt", Timestamp.from(Instant.now()))

        jdbcTemplate.update(
            """
            UPDATE posts
               SET processing_status = :processingStatus,
                   updated_at = :updatedAt
             WHERE id = :id
            """.trimIndent(),
            params,
        )

        return postRepository.findById(postId).orElse(null)
    }

    private fun insertArticle(workItem: PostWorkItem, article: ExtractedArticle, processingStatus: String): PostRecord =
        postRepository.save(
            PostRecord(
                url = article.url,
                sourceType = article.sourceType.name,
                title = article.title,
                description = article.description,
                author = article.author,
                publishedAt = article.publishedAt,
                sitemapLastmod = workItem.entry.sitemapLastmod,
                discoveredAt = workItem.entry.discoveredAt,
                contentHash = article.contentHash,
                articleBody = article.bodyText,
                processingStatus = processingStatus,
            ),
        )

    private fun updateKnownArticle(workItem: PostWorkItem, article: ExtractedArticle): PostApplyResult {
        val existing = postRepository.findByUrl(article.url)
            ?: return apply(workItem.copy(mode = PostWorkMode.NEW), article)

        if (existing.contentHash == article.contentHash) {
            refreshSitemapLastmod(article.url, workItem.entry.sitemapLastmod)
            return PostApplyResult(PostApplyKind.SKIPPED, article.url, postRepository.findByUrl(article.url))
        }

        val now = Timestamp.from(Instant.now())
        val params = MapSqlParameterSource()
            .addValue("url", article.url)
            .addValue("sourceType", article.sourceType.name)
            .addValue("title", article.title)
            .addValue("description", article.description)
            .addValue("author", article.author)
            .addValue("publishedAt", article.publishedAt?.let(Timestamp::from), Types.TIMESTAMP)
            .addValue("sitemapLastmod", workItem.entry.sitemapLastmod?.let(Timestamp::from), Types.TIMESTAMP)
            .addValue("contentHash", article.contentHash)
            .addValue("articleBody", article.bodyText)
            .addValue("updatedAt", now)

        jdbcTemplate.update(
            """
            UPDATE posts
               SET source_type = :sourceType,
                   title = :title,
                   description = :description,
                   author = :author,
                   published_at = :publishedAt,
                   sitemap_lastmod = :sitemapLastmod,
                   content_hash = :contentHash,
                   article_body = :articleBody,
                   updated_at = :updatedAt
             WHERE url = :url
            """.trimIndent(),
            params,
        )

        return PostApplyResult(PostApplyKind.UPDATED, article.url, postRepository.findByUrl(article.url))
    }

    private fun refreshSitemapLastmod(url: String, sitemapLastmod: Instant?) {
        val params = MapSqlParameterSource()
            .addValue("url", url)
            .addValue("sitemapLastmod", sitemapLastmod?.let(Timestamp::from), Types.TIMESTAMP)
            .addValue("updatedAt", Timestamp.from(Instant.now()))

        jdbcTemplate.update(
            """
            UPDATE posts
               SET sitemap_lastmod = :sitemapLastmod,
                   updated_at = :updatedAt
             WHERE url = :url
            """.trimIndent(),
            params,
        )
    }

    private fun shouldCheckForUpdate(existing: PostRecord, candidate: SitemapEntry): Boolean {
        val candidateLastmod = candidate.sitemapLastmod ?: return false
        return existing.sitemapLastmod != candidateLastmod
    }
}

data class PostStatePlan(
    val workItems: List<PostWorkItem>,
    val skippedCount: Int,
)

data class PostWorkItem(
    val entry: SitemapEntry,
    val mode: PostWorkMode,
)

enum class PostWorkMode {
    SEED,
    NEW,
    UPDATE_CHECK,
}

data class PostApplyResult(
    val kind: PostApplyKind,
    val url: String,
    val post: PostRecord? = null,
)

enum class PostApplyKind {
    SEEDED,
    NEW,
    UPDATED,
    SKIPPED,
}
