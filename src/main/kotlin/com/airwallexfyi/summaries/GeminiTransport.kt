package com.airwallexfyi.summaries

import com.airwallexfyi.http.RestClientTimeouts
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

interface GeminiTransport {
    fun generateContent(model: String, apiKey: String, requestBody: Map<String, Any>): String
}

@Component
class RestClientGeminiTransport : GeminiTransport {
    private val restClient = RestClient.builder()
        .baseUrl("https://generativelanguage.googleapis.com")
        .requestFactory(RestClientTimeouts.requestFactory())
        .build()

    override fun generateContent(model: String, apiKey: String, requestBody: Map<String, Any>): String {
        if (apiKey.isBlank()) {
            throw SummaryGenerationException("Gemini API key is not configured")
        }

        return try {
            restClient.post()
                .uri("/v1beta/models/{model}:generateContent", model)
                .header("x-goog-api-key", apiKey)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String::class.java)
                ?: throw SummaryGenerationException("Gemini returned an empty response body")
        } catch (ex: RestClientException) {
            throw SummaryGenerationException("Gemini request failed: ${ex.safeMessage()}", ex)
        }
    }

    private fun Throwable.safeMessage(): String =
        (message ?: javaClass.simpleName).lineSequence().firstOrNull()?.take(240) ?: javaClass.simpleName
}
