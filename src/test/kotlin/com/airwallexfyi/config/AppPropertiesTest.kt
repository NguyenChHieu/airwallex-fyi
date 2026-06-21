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
                "airwallex-fyi.ai.provider=gemini",
                "airwallex-fyi.ai.model=gemini-test",
                "airwallex-fyi.gemini.api-key=test-gemini-key",
                "airwallex-fyi.twilio.account-sid=test-sid",
                "airwallex-fyi.twilio.auth-token=test-token",
                "airwallex-fyi.twilio.whatsapp-from=whatsapp:+15550000001",
                "airwallex-fyi.whatsapp.to=whatsapp:+15550000002",
                "airwallex-fyi.scheduler.enabled=true",
                "airwallex-fyi.scheduler.fixed-delay-ms=120000",
                "airwallex-fyi.source.sitemap-url=https://www.airwallex.com/global/sitemap-blog.xml",
                "airwallex-fyi.source.first-run-seed-limit=10",
                "airwallex-fyi.dry-run=false",
                "airwallex-fyi.admin.token=test-admin-token",
            )
            .run { context ->
                val properties = context.getBean(AppProperties::class.java)

                assertThat(properties.ai.provider).isEqualTo("gemini")
                assertThat(properties.ai.model).isEqualTo("gemini-test")
                assertThat(properties.gemini.apiKey).isEqualTo("test-gemini-key")
                assertThat(properties.twilio.accountSid).isEqualTo("test-sid")
                assertThat(properties.twilio.authToken).isEqualTo("test-token")
                assertThat(properties.twilio.whatsappFrom).isEqualTo("whatsapp:+15550000001")
                assertThat(properties.whatsapp.to).isEqualTo("whatsapp:+15550000002")
                assertThat(properties.scheduler.enabled).isTrue()
                assertThat(properties.scheduler.fixedDelayMs).isEqualTo(120000)
                assertThat(properties.source.sitemapUrl).isEqualTo("https://www.airwallex.com/global/sitemap-blog.xml")
                assertThat(properties.source.firstRunSeedLimit).isEqualTo(10)
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
            assertThat(properties.ai.provider).isEqualTo("gemini")
            assertThat(properties.ai.model).isEqualTo("gemini-3.5-flash")
            assertThat(properties.gemini.apiKey).isBlank()
            assertThat(properties.twilio.authToken).isBlank()
            assertThat(properties.source.sitemapUrl).isEqualTo("https://www.airwallex.com/global/sitemap-blog.xml")
            assertThat(properties.source.firstRunSeedLimit).isEqualTo(25)
            assertThat(properties.admin.token).isEqualTo("dev-admin-token")
        }
    }

    @Test
    fun `rejects non positive first run seed limit`() {
        contextRunner
            .withPropertyValues("airwallex-fyi.source.first-run-seed-limit=0")
            .run { context ->
                assertThat(context).hasFailed()
            }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties::class)
    private class TestConfig
}
