package com.airwallexfyi.monitor

import com.airwallexfyi.articles.ExtractedArticle
import com.airwallexfyi.articles.ExtractionSource
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.SourceType
import com.airwallexfyi.sources.SitemapEntry
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    properties = [
        "airwallex-fyi.source.first-run-seed-limit=25",
        "spring.datasource.url=jdbc:h2:mem:post_state_service_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    ],
)
class PostStateServiceTest @Autowired constructor(
    private val postRepository: PostRepository,
    private val postStateService: PostStateService,
) {
    @BeforeEach
    fun deleteRows() {
        postRepository.deleteAll()
    }

    @Test
    fun `empty database extracts recent seed limit and baselines older urls`() {
        val candidates = (0 until 30).map { candidate(it) }

        val plan = postStateService.planWork(candidates)

        assertThat(plan.workItems).hasSize(30)
        assertThat(plan.workItems.take(25)).allSatisfy { item -> assertThat(item.mode).isEqualTo(PostWorkMode.SEED) }
        assertThat(plan.workItems.drop(25)).allSatisfy { item -> assertThat(item.mode).isEqualTo(PostWorkMode.BASELINE) }
        assertThat(plan.skippedCount).isZero()
        assertThat(plan.workItems.first().entry.url).endsWith("/article-29")

        plan.workItems.forEach { item ->
            assertThat(postStateService.apply(item, articleFor(item.entry))).extracting(PostApplyResult::kind)
                .isEqualTo(PostApplyKind.SEEDED)
        }

        assertThat(postRepository.count()).isEqualTo(30)
        assertThat(postRepository.findByUrl("https://www.airwallex.com/global/blog/article-29")?.processingStatus)
            .isEqualTo("SEEDED")
        val baselined = postRepository.findByUrl("https://www.airwallex.com/global/blog/article-0")
        assertThat(baselined?.processingStatus).isEqualTo("SEEDED")
        assertThat(baselined?.articleBody).isNull()
        assertThat(baselined?.contentHash).isNull()
    }

    @Test
    fun `second run after partial seed baseline treats older urls as known`() {
        val candidates = (0 until 30).map { candidate(it) }
        postStateService.planWork(candidates).workItems.forEach { item ->
            postStateService.apply(item, articleFor(item.entry))
        }

        val secondPlan = postStateService.planWork(candidates)

        assertThat(secondPlan.workItems).isEmpty()
        assertThat(secondPlan.skippedCount).isEqualTo(30)
        assertThat(postRepository.count()).isEqualTo(30)
    }

    @Test
    fun `second run with same known urls creates no duplicates`() {
        val candidates = (0 until 25).map { candidate(it) }
        postStateService.planWork(candidates).workItems.forEach { item ->
            postStateService.apply(item, articleFor(item.entry))
        }

        val secondPlan = postStateService.planWork(candidates)

        assertThat(secondPlan.workItems).isEmpty()
        assertThat(secondPlan.skippedCount).isEqualTo(25)
        assertThat(postRepository.count()).isEqualTo(25)
    }

    @Test
    fun `known url content hash changes update the existing row without creating a duplicate`() {
        val original = candidate(1, lastmod = Instant.parse("2026-06-20T00:00:00Z"))
        val seedWork = postStateService.planWork(listOf(original)).workItems.single()
        postStateService.apply(seedWork, articleFor(original, hash = "old-hash", title = "Old title"))

        val changed = original.copy(sitemapLastmod = Instant.parse("2026-06-21T00:00:00Z"))
        val updateWork = postStateService.planWork(listOf(changed)).workItems.single()
        val result = postStateService.apply(updateWork, articleFor(changed, hash = "new-hash", title = "New title"))

        val updatedCount = listOf(result).count { it.kind == PostApplyKind.UPDATED }

        assertThat(result.kind).isEqualTo(PostApplyKind.UPDATED)
        assertThat(updatedCount).isEqualTo(1)
        assertThat(postRepository.count()).isEqualTo(1)
        val updated = postRepository.findByUrl(original.url)
        assertThat(updated?.contentHash).isEqualTo("new-hash")
        assertThat(updated?.title).isEqualTo("New title")
        assertThat(updated?.sitemapLastmod).isEqualTo(Instant.parse("2026-06-21T00:00:00Z"))
    }

    @Test
    fun `known url with same content hash refreshes lastmod but remains skipped`() {
        val original = candidate(2, lastmod = Instant.parse("2026-06-20T00:00:00Z"))
        val seedWork = postStateService.planWork(listOf(original)).workItems.single()
        postStateService.apply(seedWork, articleFor(original, hash = "same-hash"))

        val refreshed = original.copy(sitemapLastmod = Instant.parse("2026-06-21T00:00:00Z"))
        val updateWork = postStateService.planWork(listOf(refreshed)).workItems.single()
        val result = postStateService.apply(updateWork, articleFor(refreshed, hash = "same-hash"))

        assertThat(result.kind).isEqualTo(PostApplyKind.SKIPPED)
        assertThat(postRepository.count()).isEqualTo(1)
        assertThat(postRepository.findByUrl(original.url)?.sitemapLastmod)
            .isEqualTo(Instant.parse("2026-06-21T00:00:00Z"))
    }

    private fun candidate(
        index: Int,
        lastmod: Instant = Instant.parse("2026-06-20T00:00:00Z").plusSeconds(index.toLong()),
    ): SitemapEntry = SitemapEntry(
        url = "https://www.airwallex.com/global/blog/article-$index",
        sourceType = SourceType.BLOG,
        sitemapLastmod = lastmod,
        discoveredAt = Instant.parse("2026-06-20T01:00:00Z").plusSeconds(index.toLong()),
    )

    private fun articleFor(
        entry: SitemapEntry,
        hash: String = "hash-${entry.url.substringAfterLast('/')}",
        title: String = "Title ${entry.url.substringAfterLast('/')}",
    ): ExtractedArticle = ExtractedArticle(
        url = entry.url,
        sourceType = entry.sourceType,
        title = title,
        description = "Description",
        author = "Airwallex",
        publishedAt = entry.sitemapLastmod,
        bodyText = "Body for ${entry.url}",
        contentHash = hash,
        extractionSource = ExtractionSource.STRUCTURED,
    )
}
