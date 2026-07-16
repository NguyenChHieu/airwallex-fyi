package com.airwallexfyi.config

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component

@Component
class RuntimeSafetyValidator(
    private val properties: AppProperties,
    private val environment: Environment,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        if (properties.dryRun || args.containsOption(RUN_ONCE_OPTION) || environment.acceptsProfiles(Profiles.of("test"))) return

        require(properties.admin.token.isNotBlank() && properties.admin.token != DEFAULT_DEV_ADMIN_TOKEN) {
            "ADMIN_TOKEN must be set to a non-default value when DRY_RUN=false"
        }
    }

    private companion object {
        const val RUN_ONCE_OPTION = "run-once"
        const val DEFAULT_DEV_ADMIN_TOKEN = "dev-admin-token"
    }
}
