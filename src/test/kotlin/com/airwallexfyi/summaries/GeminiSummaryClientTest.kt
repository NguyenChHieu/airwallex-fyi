package com.airwallexfyi.summaries

import com.airwallexfyi.articles.ExtractedArticle
import com.airwallexfyi.articles.ExtractionSource
import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.posts.SourceType
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

class GeminiSummaryClientTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `builds documented structured output request and parses valid summary`() {
        val transport = CapturingGeminiTransport(
            response = geminiResponse(
                """
                {
                  "headline": "Airwallex updates its platform",
                  "bullets": ["New payment tooling shipped", "Finance teams get faster workflows", "The update links back to the source"],
                  "why_it_matters": "It points to Airwallex investing in operational finance workflows.",
                  "tags": ["payments", "platform"],
                  "source_type": "BLOG"
                }
                """.trimIndent(),
            ),
        )
        val client = client(transport)

        val summary = client.summarize(article())

        assertThat(summary.headline).isEqualTo("Airwallex updates its platform")
        assertThat(summary.bullets).hasSize(3)
        assertThat(summary.whyItMatters).contains("operational finance")
        assertThat(summary.tags).containsExactly("payments", "platform")
        assertThat(summary.sourceType).isEqualTo(SourceType.BLOG)
        assertThat(transport.model).isEqualTo("gemini-test")
        assertThat(transport.apiKey).isEqualTo("test-key")
        assertThat(transport.requestBody["generationConfig"].toString()).contains("responseMimeType=application/json")
        assertThat(transport.requestBody["generationConfig"].toString()).contains("responseSchema")
        assertThat(transport.requestBody["generationConfig"].toString()).contains("headline")
        assertThat(transport.requestBody["generationConfig"].toString()).contains("why_it_matters")
        assertThat(transport.requestBody["generationConfig"].toString()).contains("source_type")
    }

    @Test
    fun `malformed candidate json fails deterministically`() {
        val client = client(CapturingGeminiTransport(geminiResponse("not-json")))

        assertThatThrownBy { client.summarize(article()) }
            .isInstanceOf(SummaryGenerationException::class.java)
            .hasMessageContaining("not valid JSON")
    }

    @Test
    fun `missing required fields fail deterministically`() {
        val client = client(
            CapturingGeminiTransport(
                geminiResponse(
                    """
                    {
                      "bullets": ["One", "Two", "Three"],
                      "why_it_matters": "Useful context",
                      "tags": ["payments"],
                      "source_type": "BLOG"
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertThatThrownBy { client.summarize(article()) }
            .isInstanceOf(SummaryGenerationException::class.java)
            .hasMessageContaining("headline")
    }

    @Test
    fun `invalid bullet counts fail through local validation`() {
        val client = client(
            CapturingGeminiTransport(
                geminiResponse(
                    """
                    {
                      "headline": "Too short",
                      "bullets": ["Only one"],
                      "why_it_matters": "Useful context",
                      "tags": ["payments"],
                      "source_type": "BLOG"
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertThatThrownBy { client.summarize(article()) }
            .isInstanceOf(SummaryValidationException::class.java)
            .hasMessageContaining("3 to 5 bullets")
    }

    @Test
    fun `provider errors do not leak api key`() {
        val transport = CapturingGeminiTransport(
            response = "",
            failure = SummaryGenerationException("Gemini request failed: upstream said no"),
        )
        val client = client(transport)

        assertThatThrownBy { client.summarize(article()) }
            .isInstanceOf(SummaryGenerationException::class.java)
            .hasMessageNotContaining("test-key")
    }

    private fun client(transport: CapturingGeminiTransport): GeminiSummaryClient = GeminiSummaryClient(
        properties = AppProperties(
            ai = AppProperties.Ai(provider = "gemini", model = "gemini-test"),
            gemini = AppProperties.Gemini(apiKey = "test-key"),
        ),
        transport = transport,
        objectMapper = objectMapper,
    )

    private fun geminiResponse(summaryJson: String): String =
        """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  { "text": ${objectMapper.writeValueAsString(summaryJson)} }
                ]
              }
            }
          ]
        }
        """.trimIndent()

    private fun article(): ExtractedArticle = ExtractedArticle(
        url = "https://www.airwallex.com/global/blog/test-summary",
        sourceType = SourceType.BLOG,
        title = "Airwallex test update",
        description = "A public update from Airwallex",
        author = "Airwallex",
        publishedAt = Instant.parse("2026-06-20T00:00:00Z"),
        bodyText = "Airwallex shared a long public update about product and platform changes. ".repeat(8),
        contentHash = "sha256:test",
        extractionSource = ExtractionSource.STRUCTURED,
    )

    private class CapturingGeminiTransport(
        private val response: String,
        private val failure: RuntimeException? = null,
    ) : GeminiTransport {
        lateinit var model: String
        lateinit var apiKey: String
        lateinit var requestBody: Map<String, Any>

        override fun generateContent(model: String, apiKey: String, requestBody: Map<String, Any>): String {
            this.model = model
            this.apiKey = apiKey
            this.requestBody = requestBody
            failure?.let { throw it }
            return response
        }
    }
}
