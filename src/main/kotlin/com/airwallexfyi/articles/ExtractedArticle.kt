package com.airwallexfyi.articles

import com.airwallexfyi.posts.SourceType
import java.time.Instant

data class ExtractedArticle(
    val url: String,
    val sourceType: SourceType,
    val title: String,
    val description: String?,
    val author: String?,
    val publishedAt: Instant?,
    val bodyText: String,
    val contentHash: String,
    val extractionSource: ExtractionSource,
    val imageUrls: List<String> = emptyList(),
)

enum class ExtractionSource {
    STRUCTURED,
    HTML_FALLBACK,
}

class ArticleExtractionException(
    val articleUrl: String,
    reason: String,
    cause: Throwable? = null,
) : RuntimeException("Failed to extract article $articleUrl: $reason", cause)