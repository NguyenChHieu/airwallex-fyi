package com.airwallexfyi.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments

class MonitorRunModeTest {
    @Test
    fun `detects run once argument`() {
        val mode = MonitorRunMode(DefaultApplicationArguments("--run-once"))

        assertThat(mode.runOnce).isTrue()
    }

    @Test
    fun `normal startup is not run once`() {
        val mode = MonitorRunMode(DefaultApplicationArguments())

        assertThat(mode.runOnce).isFalse()
    }
}
