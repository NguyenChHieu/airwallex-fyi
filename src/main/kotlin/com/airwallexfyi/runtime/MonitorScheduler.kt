package com.airwallexfyi.runtime

import com.airwallexfyi.monitor.MonitorRunService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "airwallex-fyi.scheduler", name = ["enabled"], havingValue = "true")
class MonitorScheduler(
    private val monitorRunService: MonitorRunService,
    private val monitorRunMode: MonitorRunMode,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${airwallex-fyi.scheduler.fixed-delay-ms}")
    fun runScheduled() {
        if (monitorRunMode.runOnce) {
            logger.info("Skipping scheduled monitor run because run-once mode is active.")
            return
        }

        val result = monitorRunService.runOnce()
        logger.info("Scheduled monitor run completed: status={} message={}", result.status, result.message)
    }
}
