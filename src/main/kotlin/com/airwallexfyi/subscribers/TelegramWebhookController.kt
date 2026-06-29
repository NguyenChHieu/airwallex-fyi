package com.airwallexfyi.subscribers

import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.notifications.toTelegramUpdateOrNull
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.JsonNode

@RestController
class TelegramWebhookController(
    private val properties: AppProperties,
    private val telegramSubscriptionService: TelegramSubscriptionService,
) {
    @PostMapping("/telegram/webhook")
    fun handleWebhook(
        @RequestHeader(name = SECRET_HEADER, required = false) providedSecret: String?,
        @RequestBody body: JsonNode,
    ): TelegramWebhookResponse {
        verifySecret(providedSecret)
        val update = body.toTelegramUpdateOrNull()
            ?: return TelegramWebhookResponse(processed = false)

        val result = telegramSubscriptionService.processWebhookUpdate(update)
        return TelegramWebhookResponse(
            processed = result.processedCount > 0,
            subscribed = result.subscribedCount,
            unsubscribed = result.unsubscribedCount,
            failed = result.failedCount,
            skipped = result.skipped,
        )
    }

    private fun verifySecret(providedSecret: String?) {
        val expectedSecret = properties.telegram.webhookSecret
        if (expectedSecret.isBlank()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "telegram webhook is not configured")
        }
        if (providedSecret == null || !tokensMatch(providedSecret, expectedSecret)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid telegram webhook secret")
        }
    }

    private fun tokensMatch(providedToken: String, expectedToken: String): Boolean =
        MessageDigest.isEqual(
            providedToken.toByteArray(StandardCharsets.UTF_8),
            expectedToken.toByteArray(StandardCharsets.UTF_8),
        )

    companion object {
        const val SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token"
    }
}

data class TelegramWebhookResponse(
    val processed: Boolean,
    val subscribed: Int = 0,
    val unsubscribed: Int = 0,
    val failed: Int = 0,
    val skipped: Boolean = false,
)
