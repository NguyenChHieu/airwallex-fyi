package com.airwallexfyi.articles

import com.airwallexfyi.posts.SourceType
import com.airwallexfyi.sources.AirwallexHttpClient
import com.airwallexfyi.sources.SitemapEntry
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@Service
class ArticleExtractor(
    private val httpClient: AirwallexHttpClient,
    private val richTextFlattener: RichTextFlattener,
    private val contentHashService: ContentHashService,
) {
    private val objectMapper = ObjectMapper()

    fun extract(entry: SitemapEntry): ExtractedArticle {
        val html = try {
            httpClient.fetchText(entry.url)
        } catch (ex: RuntimeException) {
            throw ArticleExtractionException(entry.url, "fetch failed: ${ex.message ?: ex.javaClass.simpleName}", ex)
        }

        val document = Jsoup.parse(html, entry.url)
        val structured = runCatching { extractStructured(document, entry) }.getOrNull()
        if (structured != null) return structured

        return runCatching { extractFallback(document, entry) }
            .getOrElse { ex ->
                if (ex is ArticleExtractionException) throw ex
                throw ArticleExtractionException(entry.url, ex.message ?: "no usable structured or fallback content", ex)
            }
    }

    private fun extractStructured(document: Document, entry: SitemapEntry): ExtractedArticle? {
        val script = document.selectFirst("script#__NEXT_DATA__") ?: return null
        val payload = script.data().ifBlank { script.html() }
        if (payload.isBlank()) return null

        val root = objectMapper.readTree(payload)
        val fields = fieldsFor(root, entry.sourceType).takeUnless { it.isMissingNode || it.isNull } ?: return null
        val title = requiredText(fields, "title") ?: return null
        val body = richTextFlattener.flatten(fields.get("content"))
        if (!body.isMeaningfulBody()) return null

        val description = optionalText(fields, "description")
        val article = ExtractedArticle(
            url = entry.url,
            sourceType = entry.sourceType,
            title = title,
            description = description,
            author = optionalText(fields, "author"),
            publishedAt = parseDate(optionalText(fields, "date")),
            bodyText = body,
            contentHash = contentHashService.hash(title, description, body),
            extractionSource = ExtractionSource.STRUCTURED,
        )
        return article
    }

    private fun extractFallback(document: Document, entry: SitemapEntry): ExtractedArticle {
        document.select("script, style, noscript, nav, header, footer, aside").remove()

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.cleanText()
            ?: document.selectFirst("h1")?.text()?.cleanText()
            ?: document.title().cleanText()

        if (title.isNullOrBlank()) {
            throw ArticleExtractionException(entry.url, "missing title")
        }

        val body = FALLBACK_BODY_SELECTORS
            .asSequence()
            .mapNotNull { selector -> document.selectFirst(selector)?.text()?.cleanText() }
            .firstOrNull { text -> text.isMeaningfulBody() }
            ?: throw ArticleExtractionException(entry.url, "missing meaningful body")

        val description = document.selectFirst("meta[name=description]")?.attr("content")?.cleanText()
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")?.cleanText()

        return ExtractedArticle(
            url = entry.url,
            sourceType = entry.sourceType,
            title = title,
            description = description,
            author = null,
            publishedAt = null,
            bodyText = body,
            contentHash = contentHashService.hash(title, description, body),
            extractionSource = ExtractionSource.HTML_FALLBACK,
        )
    }

    private fun fieldsFor(root: JsonNode, sourceType: SourceType): JsonNode {
        val pageData = root.path("props").path("pageProps").path("pageQuery").path("pageData")
        return when (sourceType) {
            SourceType.BLOG -> pageData.path("post").path("fields")
            SourceType.NEWSROOM -> pageData.path("pr").path("fields")
        }
    }

    private fun requiredText(node: JsonNode, field: String): String? =
        optionalText(node, field)?.takeIf { it.isNotBlank() }

    private fun optionalText(node: JsonNode, field: String): String? =
        node.get(field)?.asText()?.cleanText()

    private fun parseDate(value: String?): Instant? {
        val date = value?.trim().orEmpty()
        if (date.isBlank()) return null

        return runCatching { Instant.parse(date) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(date).toInstant() }.getOrNull()
            ?: runCatching { LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC) }.getOrNull()
    }


    private fun String?.cleanText(): String? =
        this?.trim()?.replace(WHITESPACE, " ")?.takeIf { it.isNotBlank() }

    private fun String.isMeaningfulBody(): Boolean = trim().length >= MIN_BODY_LENGTH

    private companion object {
        const val MIN_BODY_LENGTH = 40
        val WHITESPACE = Regex("\\s+")
        val FALLBACK_BODY_SELECTORS = listOf("article", "main", "[role=main]")
    }
}
