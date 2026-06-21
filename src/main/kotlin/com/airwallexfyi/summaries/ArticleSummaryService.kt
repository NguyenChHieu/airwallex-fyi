package com.airwallexfyi.summaries

import com.airwallexfyi.articles.ExtractedArticle
import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.posts.PostRecord
import java.sql.Timestamp
import java.time.Instant
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

@Service
class ArticleSummaryService(
    private val aiSummaryClient: AiSummaryClient,
    private val summaryRepository: SummaryRepository,
    private val objectMapper: ObjectMapper,
    private val properties: AppProperties,
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun summarize(post: PostRecord, article: ExtractedArticle, replaceExisting: Boolean = false): ArticleSummaryResult = try {
        val summary = aiSummaryClient.summarize(article)
        val record = persistSummary(post, summary, replaceExisting)
        ArticleSummaryResult.Success(summary = summary, record = record)
    } catch (ex: RuntimeException) {
        ArticleSummaryResult.Failure(reason = ex.boundedReason())
    }

    private fun persistSummary(post: PostRecord, summary: StructuredSummary, replaceExisting: Boolean): SummaryRecord {
        val record = SummaryRecord.from(
            postId = post.identifier(),
            summary = summary,
            model = properties.ai.model,
            promptVersion = PROMPT_VERSION,
            objectMapper = objectMapper,
        )
        val existing = summaryRepository.findByPostId(post.identifier())
        if (replaceExisting && existing != null) {
            updateExistingSummary(existing, record)
            return requireNotNull(summaryRepository.findByPostId(post.identifier()))
        }
        return summaryRepository.save(record)
    }

    private fun updateExistingSummary(existing: SummaryRecord, replacement: SummaryRecord) {
        val now = Timestamp.from(Instant.now())
        val params = MapSqlParameterSource()
            .addValue("id", existing.identifier())
            .addValue("headline", replacement.headline)
            .addValue("summaryJson", replacement.summaryJson)
            .addValue("whyItMatters", replacement.whyItMatters)
            .addValue("tagsJson", replacement.tagsJson)
            .addValue("model", replacement.model)
            .addValue("promptVersion", replacement.promptVersion)
            .addValue("updatedAt", now)

        jdbcTemplate.update(
            """
            UPDATE summaries
               SET headline = :headline,
                   summary_json = :summaryJson,
                   why_it_matters = :whyItMatters,
                   tags_json = :tagsJson,
                   model = :model,
                   prompt_version = :promptVersion,
                   updated_at = :updatedAt
             WHERE id = :id
            """.trimIndent(),
            params,
        )
    }

    private fun Throwable.boundedReason(): String =
        (message ?: javaClass.simpleName).lineSequence().firstOrNull()?.take(240) ?: javaClass.simpleName

    companion object {
        const val PROMPT_VERSION = "gemini-summary-v1"
    }
}

sealed interface ArticleSummaryResult {
    data class Success(
        val summary: StructuredSummary,
        val record: SummaryRecord,
    ) : ArticleSummaryResult

    data class Failure(
        val reason: String,
    ) : ArticleSummaryResult
}
