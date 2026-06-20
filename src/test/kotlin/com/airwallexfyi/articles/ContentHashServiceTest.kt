package com.airwallexfyi.articles

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ContentHashServiceTest {
    private val service = ContentHashService()

    @Test
    fun `normalizes whitespace before hashing`() {
        val compact = service.hash(
            title = "Airwallex update",
            description = "Short description",
            bodyText = "First paragraph Second paragraph",
        )
        val spaced = service.hash(
            title = "  Airwallex   update  ",
            description = "Short\n\tdescription",
            bodyText = "First   paragraph\nSecond paragraph",
        )

        assertThat(spaced).isEqualTo(compact)
    }

    @Test
    fun `title description and body changes affect the hash`() {
        val base = service.hash("Title", "Description", "Body text")

        assertThat(service.hash("Title changed", "Description", "Body text")).isNotEqualTo(base)
        assertThat(service.hash("Title", "Description changed", "Body text")).isNotEqualTo(base)
        assertThat(service.hash("Title", "Description", "Body text changed")).isNotEqualTo(base)
    }
}