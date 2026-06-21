package com.airwallexfyi.summaries

import com.airwallexfyi.posts.SourceType

private const val MIN_BULLETS = 3
private const val MAX_BULLETS = 5

data class StructuredSummary private constructor(
    val headline: String,
    val bullets: List<String>,
    val whyItMatters: String,
    val tags: List<String>,
    val sourceType: SourceType,
) {
    companion object {
        fun validated(
            headline: String,
            bullets: List<String>,
            whyItMatters: String,
            tags: List<String>,
            sourceType: SourceType,
        ): StructuredSummary {
            val normalizedHeadline = headline.trim()
            val normalizedBullets = bullets.map { it.trim() }
            val normalizedWhyItMatters = whyItMatters.trim()
            val normalizedTags = tags.map { it.trim() }

            if (normalizedHeadline.isBlank()) {
                throw SummaryValidationException("headline must not be blank")
            }
            if (normalizedBullets.size !in MIN_BULLETS..MAX_BULLETS) {
                throw SummaryValidationException("summary must contain 3 to 5 bullets")
            }
            if (normalizedBullets.any { it.isBlank() }) {
                throw SummaryValidationException("bullets must not be blank")
            }
            if (normalizedWhyItMatters.isBlank()) {
                throw SummaryValidationException("whyItMatters must not be blank")
            }
            if (normalizedTags.isEmpty() || normalizedTags.any { it.isBlank() }) {
                throw SummaryValidationException("tags must not be empty or blank")
            }

            return StructuredSummary(
                headline = normalizedHeadline,
                bullets = normalizedBullets,
                whyItMatters = normalizedWhyItMatters,
                tags = normalizedTags,
                sourceType = sourceType,
            )
        }
    }
}

class SummaryValidationException(message: String) : IllegalArgumentException(message)
