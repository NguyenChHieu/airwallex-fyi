package com.airwallexfyi.util

import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FlexibleInstantParserTest {
    @Test
    fun `parses a full instant`() {
        assertThat(FlexibleInstantParser.parse("2026-06-18T10:15:30Z"))
            .isEqualTo(Instant.parse("2026-06-18T10:15:30Z"))
    }

    @Test
    fun `parses an offset date-time`() {
        assertThat(FlexibleInstantParser.parse("2026-06-18T10:15:30+10:00"))
            .isEqualTo(Instant.parse("2026-06-18T00:15:30Z"))
    }

    @Test
    fun `parses a bare date as start of day utc`() {
        assertThat(FlexibleInstantParser.parse("2026-06-18"))
            .isEqualTo(Instant.parse("2026-06-18T00:00:00Z"))
    }

    @Test
    fun `returns null for blank or unparseable input`() {
        assertThat(FlexibleInstantParser.parse(null)).isNull()
        assertThat(FlexibleInstantParser.parse("")).isNull()
        assertThat(FlexibleInstantParser.parse("   ")).isNull()
        assertThat(FlexibleInstantParser.parse("not a date")).isNull()
    }
}
