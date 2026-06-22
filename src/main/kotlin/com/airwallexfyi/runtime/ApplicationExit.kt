package com.airwallexfyi.runtime

import kotlin.system.exitProcess
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.stereotype.Component

interface ApplicationExit {
    fun exit(code: Int)
}

@Component
class SpringApplicationExit(
    private val applicationContext: ConfigurableApplicationContext,
) : ApplicationExit {
    override fun exit(code: Int) {
        val exitCode = SpringApplication.exit(applicationContext, ExitCodeGenerator { code })
        exitProcess(exitCode)
    }
}
