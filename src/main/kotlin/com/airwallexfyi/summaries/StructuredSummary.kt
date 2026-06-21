package com.airwallexfyi.summaries

import com.airwallexfyi.posts.SourceType

private const val MIN_BULLETS = 3
private const val MAX_BULLETS = 5

class StructuredSummary private constructor(
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

    override fun equals(other: Any?): Boolean = other is StructuredSummary &&
        headline == other.headline &&
        bullets == other.bullets &&
        whyItMatters == other.whyItMatters &&
        tags == other.tags &&
        sourceType == other.sourceType

    override fun hashCode(): Int = listOf(headline, bullets, whyItMatters, tags, sourceType).hashCode()

    override fun toString(): String =
        "StructuredSummary(headline=$headline, bullets=$bullets, whyItMatters=$whyItMatters, tags=$tags, sourceType=$sourceType)"
}

class SummaryValidationException(message: String) : IllegalArgumentException(message)
