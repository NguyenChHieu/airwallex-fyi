package com.airwallexfyi.runtime

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class RunOnceApplicationRunner(
    private val monitorRunMode: MonitorRunMode,
    private val monitorRunOnceCommand: MonitorRunOnceCommand,
    private val applicationExit: ApplicationExit,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (!monitorRunMode.runOnce) return

        logger.info("Run-once mode detected; executing monitor.")
        val exitCode = monitorRunOnceCommand.execute()
        logger.info("Run-once monitor finished with exitCode={}", exitCode)
        applicationExit.exit(exitCode)
    }
}
