package com.airwallexfyi.sources

import com.airwallexfyi.posts.SourceType
import java.time.Instant

data class SitemapEntry(
    val url: String,
    val sourceType: SourceType,
    val sitemapLastmod: Instant?,
    val discoveredAt: Instant = Instant.now(),
)