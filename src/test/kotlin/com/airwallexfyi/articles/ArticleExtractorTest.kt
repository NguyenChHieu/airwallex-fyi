package com.airwallexfyi.articles

import com.airwallexfyi.posts.SourceType
import com.airwallexfyi.sources.AirwallexHttpClient
import com.airwallexfyi.sources.SitemapEntry
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ArticleExtractorTest {
    private val contentHashService = ContentHashService()

    @Test
    fun `extracts blog article from verified structured page data`() {
        val entry = entry(
            url = "https://www.airwallex.com/global/blog/introducing-airwallex-agentos-manage-your-financial-operations-in-your-preferred-agent-environment",
            sourceType = SourceType.BLOG,
        )
        val article = extractor(fixture("/fixtures/airwallex/blog-agentos.html")).extract(entry)

        assertThat(article.url).isEqualTo(entry.url)
        assertThat(article.sourceType).isEqualTo(SourceType.BLOG)
        assertThat(article.title).isEqualTo("Introducing Airwallex AgentOS")
        assertThat(article.description).isEqualTo("Manage your financial operations in your preferred agent environment.")
        assertThat(article.author).isEqualTo("Airwallex Editorial Team")
        assertThat(article.publishedAt).isEqualTo(Instant.parse("2026-06-18T10:15:30Z"))
        assertThat(article.bodyText).contains("AgentOS for finance teams")
        assertThat(article.bodyText).contains("global accounts and reduce manual follow-up")
        assertThat(article.contentHash).hasSize(64)
        assertThat(article.extractionSource).isEqualTo(ExtractionSource.STRUCTURED)
    }

    @Test
    fun `extracts newsroom article from verified structured page data`() {
        val entry = entry(
            url = "https://www.airwallex.com/global/newsroom/airwallex-acquires-leapfin-expanding-financial-lifecycle-capabilities",
            sourceType = SourceType.NEWSROOM,
        )
        val article = extractor(fixture("/fixtures/airwallex/newsroom-leapfin.html")).extract(entry)

        assertThat(article.sourceType).isEqualTo(SourceType.NEWSROOM)
        assertThat(article.title).isEqualTo("Airwallex acquires Leapfin, expanding financial lifecycle capabilities")
        assertThat(article.description).isEqualTo("The acquisition expands Airwallex financial data and revenue automation capabilities.")
        assertThat(article.author).isNull()
        assertThat(article.publishedAt).isEqualTo(Instant.parse("2026-06-17T00:00:00Z"))
        assertThat(article.bodyText).contains("A broader financial lifecycle platform")
        assertThat(article.bodyText).contains("Finance teams get cleaner revenue and transaction data")
        assertThat(article.extractionSource).isEqualTo(ExtractionSource.STRUCTURED)
    }

    @Test
    fun `falls back to readable html when structured data is unavailable`() {
        val entry = entry("https://www.airwallex.com/global/blog/fallback-update", SourceType.BLOG)
        val article = extractor(fixture("/fixtures/airwallex/fallback-article.html")).extract(entry)

        assertThat(article.title).isEqualTo("Fallback Airwallex Update")
        assertThat(article.bodyText).contains("structured data could not be parsed")
        assertThat(article.bodyText).contains("avoid failing the whole monitor run")
        assertThat(article.publishedAt).isNull()
        assertThat(article.extractionSource).isEqualTo(ExtractionSource.HTML_FALLBACK)
    }

    @Test
    fun `keeps missing optional date as null`() {
        val html = """
            <!doctype html>
            <html><body>
            <script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"pageQuery":{"pageData":{"post":{"fields":{"title":"No Date Blog","description":"No date here","content":{"nodeType":"document","content":[{"nodeType":"paragraph","content":[{"nodeType":"text","value":"This article has enough body text to be meaningful without a date."}]}]}}}}}}}}
            </script>
            </body></html>
        """.trimIndent()

        val article = extractor(html).extract(entry("https://www.airwallex.com/global/blog/no-date", SourceType.BLOG))

        assertThat(article.publishedAt).isNull()
        assertThat(article.extractionSource).isEqualTo(ExtractionSource.STRUCTURED)
    }

    @Test
    fun `fails extraction when title or meaningful body is missing`() {
        val html = """
            <!doctype html>
            <html><body>
            <script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"pageQuery":{"pageData":{"post":{"fields":{"title":"","content":{"nodeType":"document","content":[]}}}}}}}}
            </script>
            </body></html>
        """.trimIndent()
        val url = "https://www.airwallex.com/global/blog/broken"

        assertThatThrownBy { extractor(html).extract(entry(url, SourceType.BLOG)) }
            .isInstanceOf(ArticleExtractionException::class.java)
            .hasMessageContaining(url)
    }

    @Test
    fun `content hash ignores media metadata`() {
        val entry = entry("https://www.airwallex.com/global/blog/hash-media", SourceType.BLOG)
        val original = extractor(fixture("/fixtures/airwallex/blog-agentos.html")).extract(entry)
        val changedMediaHtml = fixture("/fixtures/airwallex/blog-agentos.html")
            .replace("https://www.airwallex.com/images/blog-agentos.png", "https://www.airwallex.com/images/changed.png")
        val changedMedia = extractor(changedMediaHtml).extract(entry)
        assertThat(changedMedia.contentHash).isEqualTo(original.contentHash)
    }

    @Test
    fun `wraps fetch failures with article url and reason`() {
        val failure = IllegalStateException("network down")
        val extractor = ArticleExtractor(
            httpClient = ThrowingHttpClient(failure),
            richTextFlattener = RichTextFlattener(),
            contentHashService = contentHashService,
        )
        val url = "https://www.airwallex.com/global/blog/fetch-failure"

        assertThatThrownBy { extractor.extract(entry(url, SourceType.BLOG)) }
            .isInstanceOf(ArticleExtractionException::class.java)
            .hasMessageContaining(url)
            .hasMessageContaining("network down")
    }

    private fun extractor(html: String): ArticleExtractor = ArticleExtractor(
        httpClient = FakeHttpClient(html),
        richTextFlattener = RichTextFlattener(),
        contentHashService = contentHashService,
    )

    private fun entry(url: String, sourceType: SourceType): SitemapEntry = SitemapEntry(
        url = url,
        sourceType = sourceType,
        sitemapLastmod = null,
    )

    private fun fixture(path: String): String =
        requireNotNull(javaClass.getResource(path)) { "Missing fixture $path" }.readText()

    private class FakeHttpClient(private val body: String) : AirwallexHttpClient {
        override fun fetchText(url: String): String = body
    }

    private class ThrowingHttpClient(private val failure: RuntimeException) : AirwallexHttpClient {
        override fun fetchText(url: String): String = throw failure
    }
}
