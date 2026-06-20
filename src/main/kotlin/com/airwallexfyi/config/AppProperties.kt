package com.airwallexfyi.config

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties(prefix = "airwallex-fyi")
@Validated
data class AppProperties(
    @field:Valid val openai: OpenAi = OpenAi(),
    @field:Valid val twilio: Twilio = Twilio(),
    @field:Valid val whatsapp: WhatsApp = WhatsApp(),
    @field:Valid val scheduler: Scheduler = Scheduler(),
    @field:Valid val source: Source = Source(),
    @field:Valid val admin: Admin = Admin(),
    val dryRun: Boolean = true,
) {
    data class OpenAi(
        val apiKey: String = "",
        val model: String = "gpt-4.1-mini",
    )

    data class Twilio(
        val accountSid: String = "",
        val authToken: String = "",
        val whatsappFrom: String = "",
    )

    data class WhatsApp(
        val to: String = "",
    )

    data class Scheduler(
        val enabled: Boolean = false,
        @field:Min(1000)
        val fixedDelayMs: Long = 300000,
    )

    data class Source(
        val sitemapUrl: String = "https://www.airwallex.com/global/sitemap-blog.xml",
        @field:Min(1)
        val firstRunSeedLimit: Int = 25,
    )

    data class Admin(
        val token: String = "dev-admin-token",
    )
}