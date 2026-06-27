package com.airwallexfyi.notifications

import com.airwallexfyi.config.AppProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.ObjectMapper

interface TelegramNotifier {
    fun send(payload: WhatsAppAlertPayload): NotificationResult
}

@Component
@ConditionalOnProperty(prefix = "airwallex-fyi", name = ["dry-run"], havingValue = "true", matchIfMissing = true)
class DryRunTelegramNotifier : TelegramNotifier {
    override fun send(payload: WhatsAppAlertPayload): NotificationResult = NotificationResult(
        status = NotificationStatus.DRY_RUN,
        payloadPreview = payload.preview,
        twilioCalled = false,
    )
}

@Component
@ConditionalOnProperty(prefix = "airwallex-fyi", name = ["dry-run"], havingValue = "false")
class TelegramBotNotifier(
    private val properties: AppProperties,
    private val transport: TelegramTransport,
) : TelegramNotifier {
    override fun send(payload: WhatsAppAlertPayload): NotificationResult = try {
        val response = transport.sendMessage(
            botToken = properties.telegram.botToken,
            chatId = payload.recipient,
            body = payload.body,
        )
        NotificationResult(
            status = NotificationStatus.SENT,
            payloadPreview = payload.preview,
            providerMessageId = response.messageId,
            twilioCalled = false,
        )
    } catch (ex: RuntimeException) {
        NotificationResult(
            status = NotificationStatus.FAILED,
            payloadPreview = payload.preview,
            errorMessage = ex.sanitizedReason(properties.telegram.botToken),
            twilioCalled = false,
        )
    }

    private fun Throwable.sanitizedReason(secret: String): String {
        val raw = (message ?: javaClass.simpleName).lineSequence().firstOrNull()?.take(ERROR_LIMIT) ?: javaClass.simpleName
        return if (secret.isBlank()) raw else raw.replace(secret, "[redacted]")
    }

    private companion object {
        const val ERROR_LIMIT = 240
    }
}

interface TelegramTransport {
    fun sendMessage(botToken: String, chatId: String, body: String): TelegramSendResponse
}

data class TelegramSendResponse(val messageId: String)

@Component
class RestClientTelegramTransport(
    private val objectMapper: ObjectMapper,
) : TelegramTransport {
    private val restClient = RestClient.builder()
        .baseUrl("https://api.telegram.org")
        .build()

    override fun sendMessage(botToken: String, chatId: String, body: String): TelegramSendResponse {
        require(botToken.isNotBlank()) { "Telegram bot token is not configured" }
        require(chatId.isNotBlank()) { "Telegram chat ID is not configured" }

        val responseBody = try {
            restClient.post()
                .uri("/bot{botToken}/sendMessage", botToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    mapOf(
                        "chat_id" to chatId,
                        "text" to body,
                    ),
                )
                .retrieve()
                .body(String::class.java)
                ?: throw IllegalStateException("Telegram returned an empty response body")
        } catch (ex: RestClientException) {
            throw IllegalStateException("Telegram request failed: ${ex.safeMessage()}", ex)
        }

        val root = objectMapper.readTree(responseBody)
        if (!root.path("ok").asBoolean(false)) {
            val description = root.path("description").asText("Telegram returned an error")
            throw IllegalStateException(description)
        }

        val messageId = root.path("result").path("message_id").asText(null).orEmpty()
        if (messageId.isBlank()) {
            throw IllegalStateException("Telegram response did not include a message ID")
        }
        return TelegramSendResponse(messageId = messageId)
    }

    private fun Throwable.safeMessage(): String =
        (message ?: javaClass.simpleName).lineSequence().firstOrNull()?.take(240) ?: javaClass.simpleName
}
