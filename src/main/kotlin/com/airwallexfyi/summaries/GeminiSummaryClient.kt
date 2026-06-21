package com.airwallexfyi.summaries

import com.airwallexfyi.articles.ExtractedArticle
import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.posts.SourceType
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@Component
class GeminiSummaryClient(
    private val properties: AppProperties,
    private val transport: GeminiTransport,
    private val objectMapper: ObjectMapper,
) : AiSummaryClient {
    override fun summarize(article: ExtractedArticle): StructuredSummary {
        if (!properties.ai.provider.equals("gemini", ignoreCase = true)) {
            throw SummaryGenerationException("Unsupported AI provider configured: ${properties.ai.provider}")
        }

        val responseBody = transport.generateContent(
            model = properties.ai.model,
            apiKey = properties.gemini.apiKey,
            requestBody = requestBodyFor(article),
        )
        val summaryJson = firstCandidateText(responseBody)
        return parseStructuredSummary(summaryJson)
    }

    private fun requestBodyFor(article: ExtractedArticle): Map<String, Any> = mapOf(
        "contents" to listOf(
            mapOf(
                "parts" to listOf(
                    mapOf("text" to promptFor(article)),
                ),
            ),
        ),
        "generationConfig" to mapOf(
            "responseFormat" to mapOf(
                "text" to mapOf(
                    "mimeType" to "application/json",
                    "schema" to summarySchema(),
                ),
            ),
        ),
    )

    private fun promptFor(article: ExtractedArticle): String = buildString {
        appendLine("Summarize this public Airwallex update for a concise WhatsApp alert.")
        appendLine("Return only JSON matching the provided schema.")
        appendLine("Use 3 to 5 short bullets. Keep tags short and useful.")
        appendLine()
        appendLine("Title: ${article.title}")
        appendLine("Description: ${article.description.orEmpty()}")
        appendLine("Source type: ${article.sourceType.name}")
        appendLine("URL: ${article.url}")
        appendLine("Body:")
        appendLine(article.bodyText.take(MAX_PROMPT_BODY_CHARS))
    }

    private fun summarySchema(): Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "headline" to mapOf("type" to "string"),
            "bullets" to mapOf(
                "type" to "array",
                "minItems" to 3,
                "maxItems" to 5,
                "items" to mapOf("type" to "string"),
            ),
            "why_it_matters" to mapOf("type" to "string"),
            "tags" to mapOf(
                "type" to "array",
                "minItems" to 1,
                "items" to mapOf("type" to "string"),
            ),
            "source_type" to mapOf(
                "type" to "string",
                "enum" to SourceType.entries.map { it.name },
            ),
        ),
        "required" to listOf("headline", "bullets", "why_it_matters", "tags", "source_type"),
    )

    private fun firstCandidateText(responseBody: String): String {
        val root = parseJson(responseBody, "Gemini response was not valid JSON")
        val text = root.path("candidates")
            .get(0)
            ?.path("content")
            ?.path("parts")
            ?.get(0)
            ?.path("text")
            ?.asText()
            ?.trim()
            .orEmpty()

        if (text.isBlank()) {
            throw SummaryGenerationException("Gemini response did not include candidate text")
        }
        return text
    }

    private fun parseStructuredSummary(summaryJson: String): StructuredSummary {
        val root = parseJson(summaryJson, "Gemini summary text was not valid JSON")
        return StructuredSummary.validated(
            headline = requiredText(root, "headline"),
            bullets = requiredStringArray(root, "bullets"),
            whyItMatters = requiredText(root, "why_it_matters"),
            tags = requiredStringArray(root, "tags"),
            sourceType = requiredSourceType(root, "source_type"),
        )
    }

    private fun parseJson(json: String, failureMessage: String): JsonNode = try {
        objectMapper.readTree(json)
    } catch (ex: RuntimeException) {
        throw SummaryGenerationException(failureMessage, ex)
    }

    private fun requiredText(root: JsonNode, field: String): String {
        val text = root.path(field).asText(null)?.trim().orEmpty()
        if (text.isBlank()) {
            throw SummaryGenerationException("Gemini summary missing required field: $field")
        }
        return text
    }

    private fun requiredStringArray(root: JsonNode, field: String): List<String> {
        val node = root.path(field)
        if (!node.isArray) {
            throw SummaryGenerationException("Gemini summary field must be an array: $field")
        }
        return (0 until node.size()).map { index -> node.get(index).asText(null).orEmpty() }
    }

    private fun requiredSourceType(root: JsonNode, field: String): SourceType = try {
        SourceType.valueOf(requiredText(root, field))
    } catch (ex: IllegalArgumentException) {
        throw SummaryGenerationException("Gemini summary has unsupported source_type", ex)
    }

    private companion object {
        const val MAX_PROMPT_BODY_CHARS = 12000
    }
}
