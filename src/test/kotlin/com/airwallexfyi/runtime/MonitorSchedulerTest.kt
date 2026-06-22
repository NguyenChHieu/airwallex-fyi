package com.airwallexfyi.runtime

import com.airwallexfyi.monitor.MonitorRunResult
import com.airwallexfyi.monitor.MonitorRunService
import com.airwallexfyi.monitor.MonitorRunStatus
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.boot.DefaultApplicationArguments

class MonitorSchedulerTest {
    @Test
    fun `scheduled execution calls monitor when not in run once mode`() {
        val service = mock(MonitorRunService::class.java)
        `when`(service.runOnce()).thenReturn(result())
        val scheduler = MonitorScheduler(
            service,
            MonitorRunMode(DefaultApplicationArguments()),
        )

        scheduler.runScheduled()

        verify(service).runOnce()
    }

    @Test
    fun `scheduled execution skips monitor when run once mode is active`() {
        val service = mock(MonitorRunService::class.java)
        val scheduler = MonitorScheduler(
            service,
            MonitorRunMode(DefaultApplicationArguments("--run-once")),
        )

        scheduler.runScheduled()

        verify(service, never()).runOnce()
    }

    private fun result(): MonitorRunResult = MonitorRunResult(
        status = MonitorRunStatus.COMPLETED,
        sitemapFetched = true,
        discoveredCount = 1,
        seededCount = 0,
        newCount = 1,
        updatedCount = 0,
        skippedCount = 0,
        failedCount = 0,
        message = "Monitor run completed.",
    )
}
