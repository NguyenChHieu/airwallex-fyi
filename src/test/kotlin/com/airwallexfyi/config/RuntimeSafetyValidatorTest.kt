package com.airwallexfyi.config

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.mock.env.MockEnvironment

class RuntimeSafetyValidatorTest {
    @Test
    fun `rejects default admin token when dry run is disabled`() {
        val validator = RuntimeSafetyValidator(
            AppProperties(dryRun = false),
            MockEnvironment(),
        )

        assertThatThrownBy { validator.run(DefaultApplicationArguments()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("ADMIN_TOKEN")
    }

    @Test
    fun `allows non default admin token when dry run is disabled`() {
        val validator = RuntimeSafetyValidator(
            AppProperties(dryRun = false, admin = AppProperties.Admin(token = "real-admin-token")),
            MockEnvironment(),
        )

        assertThatCode { validator.run(DefaultApplicationArguments()) }.doesNotThrowAnyException()
    }

    @Test
    fun `allows run once worker without admin token`() {
        val validator = RuntimeSafetyValidator(
            AppProperties(dryRun = false),
            MockEnvironment(),
        )

        assertThatCode { validator.run(DefaultApplicationArguments("--run-once")) }.doesNotThrowAnyException()
    }

    @Test
    fun `skips guard in test profile`() {
        val validator = RuntimeSafetyValidator(
            AppProperties(dryRun = false),
            MockEnvironment().withProperty("spring.profiles.active", "test"),
        )

        assertThatCode { validator.run(DefaultApplicationArguments()) }.doesNotThrowAnyException()
    }
}
