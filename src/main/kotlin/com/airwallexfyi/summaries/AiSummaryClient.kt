package com.airwallexfyi.summaries

import com.airwallexfyi.articles.ExtractedArticle

interface AiSummaryClient {
    fun summarize(article: ExtractedArticle): StructuredSummary
}

class SummaryGenerationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
