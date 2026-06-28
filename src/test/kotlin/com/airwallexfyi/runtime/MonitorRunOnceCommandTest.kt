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
    fun `article-only partial failure exits zero`() {
        val service = mock(MonitorRunService::class.java)
        `when`(service.runOnce()).thenReturn(result(MonitorRunStatus.PARTIAL_FAILURE, failedCount = 1))
        val command = DefaultMonitorRunOnceCommand(service)

        assertThat(command.execute()).isEqualTo(DefaultMonitorRunOnceCommand.EXIT_SUCCESS)
        verify(service).runOnce()
    }

    @Test
    fun `summary partial failure exits one`() {
        val service = mock(MonitorRunService::class.java)
        `when`(service.runOnce()).thenReturn(result(MonitorRunStatus.PARTIAL_FAILURE, summaryFailedCount = 1))
        val command = DefaultMonitorRunOnceCommand(service)

        assertThat(command.execute()).isEqualTo(DefaultMonitorRunOnceCommand.EXIT_FAILURE)
        verify(service).runOnce()
    }

    @Test
    fun `digest partial failure exits one`() {
        val service = mock(MonitorRunService::class.java)
        `when`(service.runOnce()).thenReturn(result(MonitorRunStatus.PARTIAL_FAILURE, digestFailedCount = 1))
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

    private fun result(
        status: String,
        failedCount: Int = 0,
        summaryFailedCount: Int = 0,
        digestFailedCount: Int = 0,
    ): MonitorRunResult = MonitorRunResult(
        status = status,
        sitemapFetched = true,
        discoveredCount = 1,
        seededCount = 0,
        newCount = 1,
        updatedCount = 0,
        skippedCount = 0,
        failedCount = failedCount,
        summaryFailedCount = summaryFailedCount,
        digestFailedCount = digestFailedCount,
        message = "Monitor run $status.",
    )
}
