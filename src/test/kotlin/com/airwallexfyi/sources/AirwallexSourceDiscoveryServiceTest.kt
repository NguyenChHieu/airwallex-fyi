package com.airwallexfyi.sources

import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.posts.SourceType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AirwallexSourceDiscoveryServiceTest {
    @Test
    fun `discovers only strict global blog and newsroom article URLs`() {
        val service = AirwallexSourceDiscoveryService(
            properties = AppProperties(),
            httpClient = FakeAirwallexHttpClient(fixture("/fixtures/airwallex/sitemap-blog.xml")),
        )

        val candidates = service.discoverCandidates()

        assertThat(candidates).hasSize(2)
        assertThat(candidates.map { it.url }).containsExactly(
            "https://www.airwallex.com/global/blog/introducing-airwallex-agentos-manage-your-financial-operations-in-your-preferred-agent-environment",
            "https://www.airwallex.com/global/newsroom/airwallex-acquires-leapfin-expanding-financial-lifecycle-capabilities",
        )
        assertThat(candidates.map { it.sourceType }).containsExactly(SourceType.BLOG, SourceType.NEWSROOM)
        assertThat(candidates[0].sitemapLastmod).isNotNull()
        assertThat(candidates[1].sitemapLastmod).isNotNull()
        assertThat(candidates[0].discoveredAt).isNotNull()
    }

    @Test
    fun `keeps missing lastmod as null`() {
        val xml = """
            <urlset>
              <url><loc>https://www.airwallex.com/global/blog/article-without-lastmod</loc></url>
            </urlset>
        """.trimIndent()
        val service = AirwallexSourceDiscoveryService(AppProperties(), FakeAirwallexHttpClient(xml))

        val candidates = service.discoverCandidates()

        val candidate = candidates.single()
        assertThat(candidate.sourceType).isEqualTo(SourceType.BLOG)
        assertThat(candidate.sitemapLastmod).isNull()
    }

    @Test
    fun `does not convert fetch failures into successful empty results`() {
        val failure = IllegalStateException("sitemap unavailable")
        val service = AirwallexSourceDiscoveryService(
            properties = AppProperties(),
            httpClient = ThrowingAirwallexHttpClient(failure),
        )

        assertThatThrownBy { service.discoverCandidates() }
            .isSameAs(failure)
    }

    private fun fixture(path: String): String =
        requireNotNull(javaClass.getResource(path)) { "Missing fixture $path" }.readText()

    private class FakeAirwallexHttpClient(private val body: String) : AirwallexHttpClient {
        override fun fetchText(url: String): String = body
    }

    private class ThrowingAirwallexHttpClient(private val failure: RuntimeException) : AirwallexHttpClient {
        override fun fetchText(url: String): String = throw failure
    }
}