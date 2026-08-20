package com.airwallexfyi.util

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Parses Airwallex's date/time strings, which show up in more than one shape across
 * sitemap lastmod values and article date fields: full instants, offset date-times,
 * or bare dates. Returns null rather than throwing on anything unparseable.
 */
object FlexibleInstantParser {
    fun parse(value: String?): Instant? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return null

        return runCatching { Instant.parse(trimmed) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(trimmed).toInstant() }.getOrNull()
            ?: runCatching { LocalDate.parse(trimmed).atStartOfDay().toInstant(ZoneOffset.UTC) }.getOrNull()
    }
}
