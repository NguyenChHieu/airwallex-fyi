package com.airwallexfyi.runtime

import org.springframework.boot.ApplicationArguments
import org.springframework.stereotype.Component

@Component
class MonitorRunMode(args: ApplicationArguments) {
    val runOnce: Boolean = args.containsOption(RUN_ONCE_OPTION)

    private companion object {
        const val RUN_ONCE_OPTION = "run-once"
    }
}
