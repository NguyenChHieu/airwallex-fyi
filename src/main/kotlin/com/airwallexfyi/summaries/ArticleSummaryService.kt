package com.airwallexfyi.summaries

import com.airwallexfyi.articles.ExtractedArticle
import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.posts.PostRecord
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

@Service
class ArticleSummaryService(
    private val aiSummaryClient: AiSummaryClient,
    private val summaryRepository: SummaryRepository,
    private val objectMapper: ObjectMapper,
    private val properties: AppProperties,
) {
    fun summarize(post: PostRecord, article: ExtractedArticle): ArticleSummaryResult = try {
        val summary = aiSummaryClient.summarize(article)
        val record = summaryRepository.save(
            SummaryRecord.from(
                postId = post.identifier(),
                summary = summary,
                model = properties.ai.model,
                promptVersion = PROMPT_VERSION,
                objectMapper = objectMapper,
            ),
        )
        ArticleSummaryResult.Success(summary = summary, record = record)
    } catch (ex: RuntimeException) {
        ArticleSummaryResult.Failure(reason = ex.boundedReason())
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
