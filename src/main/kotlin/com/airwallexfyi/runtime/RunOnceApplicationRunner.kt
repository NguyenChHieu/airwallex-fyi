package com.airwallexfyi.runtime

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class RunOnceApplicationRunner(
    private val monitorRunMode: MonitorRunMode,
    private val monitorRunOnceCommand: MonitorRunOnceCommand,
    private val applicationExit: ApplicationExit,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        if (!monitorRunMode.runOnce) return

        applicationExit.exit(monitorRunOnceCommand.execute())
    }
}
