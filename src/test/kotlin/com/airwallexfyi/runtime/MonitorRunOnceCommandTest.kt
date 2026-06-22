package com.airwallexfyi.runtime

import com.airwallexfyi.monitor.MonitorRunResult
import com.airwallexfyi.monitor.MonitorRunService
import com.airwallexfyi.monitor.MonitorRunStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class MonitorRunOnceCommandTest {
    @Test
    fun `completed monitor run exits zero and calls service once`() {
        val service = mock(MonitorRunService::class.java)
        `when`(service.runOnce()).thenReturn(result(MonitorRunStatus.COMPLETED))
        val command = DefaultMonitorRunOnceCommand(service)

        val exitCode = command.execute()

        assertThat(exitCode).isEqualTo(DefaultMonitorRunOnceCommand.EXIT_SUCCESS)
        verify(service).runOnce()
    }

    @Test
    fun `partial failure exits one`() {
        val service = mock(MonitorRunService::class.java)
        `when`(service.runOnce()).thenReturn(result(MonitorRunStatus.PARTIAL_FAILURE))
        val command = DefaultMonitorRunOnceCommand(service)

        assertThat(command.execute()).isEqualTo(DefaultMonitorRunOnceCommand.EXIT_FAILURE)
        verify(service).runOnce()
    }

    @Test
    fun `failed run exits one`() {
        val service = mock(MonitorRunService::class.java)
        `when`(service.runOnce()).thenReturn(result(MonitorRunStatus.FAILED))
        val command = DefaultMonitorRunOnceCommand(service)

        assertThat(command.execute()).isEqualTo(DefaultMonitorRunOnceCommand.EXIT_FAILURE)
        verify(service).runOnce()
    }

    private fun result(status: String): MonitorRunResult = MonitorRunResult(
        status = status,
        sitemapFetched = true,
        discoveredCount = 1,
        seededCount = 0,
        newCount = 1,
        updatedCount = 0,
        skippedCount = 0,
        failedCount = 0,
        message = "Monitor run $status.",
    )
}
