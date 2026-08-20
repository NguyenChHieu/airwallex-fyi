package com.airwallexfyi.sources

import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.posts.SourceType
import com.airwallexfyi.util.FlexibleInstantParser
import java.net.URI
import java.time.Instant
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.springframework.stereotype.Service

@Service
class AirwallexSourceDiscoveryService(
    private val properties: AppProperties,
    private val httpClient: AirwallexHttpClient,
) {
    fun discoverCandidates(): List<SitemapEntry> {
        val xml = httpClient.fetchText(properties.source.sitemapUrl)
        val document = Jsoup.parse(xml, "", Parser.xmlParser())
        val discoveredAt = Instant.now()

        return document.select("url")
            .mapNotNull { urlElement ->
                val loc = urlElement.selectFirst("loc")?.text()?.trim().orEmpty()
                val sourceType = sourceTypeFor(loc) ?: return@mapNotNull null
                SitemapEntry(
                    url = loc,
                    sourceType = sourceType,
                    sitemapLastmod = FlexibleInstantParser.parse(urlElement.selectFirst("lastmod")?.text()),
                    discoveredAt = discoveredAt,
                )
            }
    }

    private fun sourceTypeFor(rawUrl: String): SourceType? {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != "www.airwallex.com") return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null

        return when {
            BLOG_PATH.matches(uri.path) -> SourceType.BLOG
            NEWSROOM_PATH.matches(uri.path) -> SourceType.NEWSROOM
            else -> null
        }
    }

    private companion object {
        val BLOG_PATH = Regex("^/global/blog/[^/]+$")
        val NEWSROOM_PATH = Regex("^/global/newsroom/[^/]+$")
    }
}