package com.airwallexfyi.persistence

import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:persistence_schema_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=true",
    ],
)
class PersistenceSchemaTest @Autowired constructor(
    private val postRepository: PostRepository,
) {
    @Test
    fun `inserts and reads post records by url`() {
        val post = postRepository.save(
            PostRecord(
                url = "https://www.airwallex.com/global/blog/test-post-${System.nanoTime()}",
                sourceType = "blog",
                title = "Test Post",
                description = "A test Airwallex update",
                author = "Airwallex",
                publishedAt = Instant.parse("2026-06-20T00:00:00Z"),
                sitemapLastmod = Instant.parse("2026-06-20T00:01:00Z"),
                discoveredAt = Instant.parse("2026-06-20T00:02:00Z"),
                contentHash = "sha256:test",
                articleBody = "Article body",
                processingStatus = "DISCOVERED",
                createdAt = Instant.parse("2026-06-20T00:03:00Z"),
                updatedAt = Instant.parse("2026-06-20T00:04:00Z"),
            ),
        )

        val found = postRepository.findByUrl(post.url)

        assertThat(found).isNotNull
        assertThat(found?.identifier()).isEqualTo(post.identifier())
        assertThat(found?.sourceType).isEqualTo("blog")
        assertThat(found?.contentHash).isEqualTo("sha256:test")
    }

    @Test
    fun `rejects duplicate post urls`() {
        val url = "https://www.airwallex.com/global/newsroom/duplicate-${System.nanoTime()}"

        postRepository.save(PostRecord(url = url, sourceType = "newsroom"))

        assertThatThrownBy {
            postRepository.save(PostRecord(url = url, sourceType = "newsroom"))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
