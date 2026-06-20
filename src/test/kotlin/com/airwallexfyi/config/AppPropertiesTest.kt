package com.airwallexfyi.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class AppPropertiesTest {
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TestConfig::class.java))

    @Test
    fun `binds application properties from environment style values`() {
        contextRunner
            .withPropertyValues(
                "airwallex-fyi.openai.api-key=test-openai-key",
                "airwallex-fyi.openai.model=gpt-test",
                "airwallex-fyi.twilio.account-sid=test-sid",
                "airwallex-fyi.twilio.auth-token=test-token",
                "airwallex-fyi.twilio.whatsapp-from=whatsapp:+15550000001",
                "airwallex-fyi.whatsapp.to=whatsapp:+15550000002",
                "airwallex-fyi.scheduler.enabled=true",
                "airwallex-fyi.scheduler.fixed-delay-ms=120000",
                "airwallex-fyi.dry-run=false",
                "airwallex-fyi.admin.token=test-admin-token",
            )
            .run { context ->
                val properties = context.getBean(AppProperties::class.java)

                assertThat(properties.openai.apiKey).isEqualTo("test-openai-key")
                assertThat(properties.openai.model).isEqualTo("gpt-test")
                assertThat(properties.twilio.accountSid).isEqualTo("test-sid")
                assertThat(properties.twilio.authToken).isEqualTo("test-token")
                assertThat(properties.twilio.whatsappFrom).isEqualTo("whatsapp:+15550000001")
                assertThat(properties.whatsapp.to).isEqualTo("whatsapp:+15550000002")
                assertThat(properties.scheduler.enabled).isTrue()
                assertThat(properties.scheduler.fixedDelayMs).isEqualTo(120000)
                assertThat(properties.dryRun).isFalse()
                assertThat(properties.admin.token).isEqualTo("test-admin-token")
            }
    }

    @Test
    fun `safe local defaults avoid external side effects`() {
        contextRunner.run { context ->
            val properties = context.getBean(AppProperties::class.java)

            assertThat(properties.dryRun).isTrue()
            assertThat(properties.scheduler.enabled).isFalse()
            assertThat(properties.openai.apiKey).isBlank()
            assertThat(properties.twilio.authToken).isBlank()
            assertThat(properties.admin.token).isEqualTo("dev-admin-token")
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties::class)
    private class TestConfig
}