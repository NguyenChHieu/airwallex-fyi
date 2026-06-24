package com.airwallexfyi.runtime

import com.airwallexfyi.monitor.MonitorRunResult
import com.airwallexfyi.monitor.MonitorRunService
import com.airwallexfyi.monitor.MonitorRunStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

interface MonitorRunOnceCommand {
    fun execute(): Int
}

@Service
class DefaultMonitorRunOnceCommand(
    private val monitorRunService: MonitorRunService,
) : MonitorRunOnceCommand {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun execute(): Int {
        val result = monitorRunService.runOnce()
        log(result)
        return if (result.status == MonitorRunStatus.COMPLETED) EXIT_SUCCESS else EXIT_FAILURE
    }

    private fun log(result: MonitorRunResult) {
        logger.info(
            "Monitor run-once completed: status={} message={} discovered={} seeded={} baselined={} new={} updated={} skipped={} failed={} summarized={} summaryFailed={} approvals={} digestSent={} digestNoChange={} digestSkippedDuplicate={} digestFailed={} digestErrors={}",
            result.status,
            result.message,
            result.discoveredCount,
            result.seededCount,
            result.baselinedCount,
            result.newCount,
            result.updatedCount,
            result.skippedCount,
            result.failedCount,
            result.summarizedCount,
            result.summaryFailedCount,
            result.approvalNeededCount,
            result.digestSentCount,
            result.digestNoChangeCount,
            result.digestSkippedDuplicateCount,
            result.digestFailedCount,
            result.sampleDigestErrors,
        )
    }

    companion object {
        const val EXIT_SUCCESS = 0
        const val EXIT_FAILURE = 1
    }
}
