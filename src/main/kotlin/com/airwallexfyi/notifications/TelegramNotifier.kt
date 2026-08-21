package com.airwallexfyi.notifications

import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.http.RestClientTimeouts
import com.airwallexfyi.util.RateLimiter
import java.time.Duration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.JsonNode
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
            telegramCalled = true,
        )
    } catch (ex: RuntimeException) {
        NotificationResult(
            status = NotificationStatus.FAILED,
            payloadPreview = payload.preview,
            errorMessage = ex.sanitizedReason(properties.telegram.botToken),
            twilioCalled = false,
            telegramCalled = true,
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

    fun getUpdates(botToken: String, offset: Long?): List<TelegramUpdate>
}

data class TelegramSendResponse(val messageId: String)

data class TelegramUpdate(
    val updateId: Long,
    val message: TelegramMessage?,
)

data class TelegramMessage(
    val text: String?,
    val chat: TelegramChat,
)

data class TelegramChat(
    val id: Long,
    val username: String?,
    val firstName: String?,
    val lastName: String?,
)

fun JsonNode.toTelegramUpdateOrNull(): TelegramUpdate? {
    val updateId = path("update_id").asLong(Long.MIN_VALUE)
    if (updateId == Long.MIN_VALUE) return null

    val messageNode = path("message")
    val chatNode = messageNode.path("chat")
    val chatId = chatNode.path("id").asLong(Long.MIN_VALUE)
    val message = if (messageNode.isMissingNode || chatId == Long.MIN_VALUE) {
        null
    } else {
        TelegramMessage(
            text = messageNode.path("text").textOrNull(),
            chat = TelegramChat(
                id = chatId,
                username = chatNode.path("username").textOrNull(),
                firstName = chatNode.path("first_name").textOrNull(),
                lastName = chatNode.path("last_name").textOrNull(),
            ),
        )
    }
    return TelegramUpdate(updateId = updateId, message = message)
}

@Component
class RestClientTelegramTransport(
    private val objectMapper: ObjectMapper,
    properties: AppProperties,
    baseUrl: String = "https://api.telegram.org",
    private val sleeper: (Duration) -> Unit = { Thread.sleep(it.toMillis()) },
) : TelegramTransport {
    private val restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(RestClientTimeouts.requestFactory())
        .build()
    // Shared across every call this transport makes (sends, confirmations, spotlight
    // replies, getUpdates polls) - it's Telegram's per-bot-token budget, not a
    // per-endpoint one.
    private val rateLimiter = RateLimiter(properties.telegram.sendsPerSecond)

    override fun sendMessage(botToken: String, chatId: String, body: String): TelegramSendResponse {
        require(botToken.isNotBlank()) { "Telegram bot token is not configured" }
        require(chatId.isNotBlank()) { "Telegram chat ID is not configured" }

        val responseBody = executeWithRetry("Telegram request failed") {
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
        }

        val root = objectMapper.readTree(responseBody)
        if (!root.path("ok").asBoolean(false)) {
            val description = root.path("description").textOrDefault("Telegram returned an error")
            throw IllegalStateException(description)
        }

        val messageId = root.path("result").path("message_id").textOrNull().orEmpty()
        if (messageId.isBlank()) {
            throw IllegalStateException("Telegram response did not include a message ID")
        }
        return TelegramSendResponse(messageId = messageId)
    }

    override fun getUpdates(botToken: String, offset: Long?): List<TelegramUpdate> {
        require(botToken.isNotBlank()) { "Telegram bot token is not configured" }

        val responseBody = executeWithRetry("Telegram getUpdates request failed") {
            restClient.get()
                .uri { uriBuilder ->
                    val builder = uriBuilder.path("/bot{botToken}/getUpdates")
                    if (offset != null) {
                        builder.queryParam("offset", offset)
                    }
                    builder.build(botToken)
                }
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(String::class.java)
                ?: throw IllegalStateException("Telegram returned an empty response body")
        }

        val root = objectMapper.readTree(responseBody)
        if (!root.path("ok").asBoolean(false)) {
            val description = root.path("description").textOrDefault("Telegram returned an error")
            throw IllegalStateException(description)
        }
        return root.path("result")
            .filter { !it.isMissingNode }
            .mapNotNull { it.toTelegramUpdateOrNull() }
    }

    // Rate-limits every call this transport makes, and retries once Telegram's own
    // rate-limit response (429, with a retry_after hint) is hit - that response must
    // be caught here, before the generic RestClientException handling below, or its
    // retry_after would be lost inside a plain IllegalStateException.
    private fun executeWithRetry(failureMessage: String, request: () -> String): String {
        var attempt = 0
        while (true) {
            rateLimiter.acquire()
            try {
                return request()
            } catch (ex: HttpClientErrorException.TooManyRequests) {
                if (attempt == MAX_RETRY_ATTEMPTS - 1) {
                    throw IllegalStateException("$failureMessage: rate limited (429) after $MAX_RETRY_ATTEMPTS attempts", ex)
                }
                val waitSeconds = retryAfterSecondsFrom(ex).coerceAtMost(MAX_RETRY_AFTER_SECONDS)
                try {
                    sleeper(Duration.ofSeconds(waitSeconds))
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IllegalStateException("$failureMessage: retry interrupted", interrupted)
                }
                attempt += 1
            } catch (ex: RestClientException) {
                throw IllegalStateException("$failureMessage: ${ex.safeMessage()}", ex)
            }
        }
    }

    private fun retryAfterSecondsFrom(ex: HttpClientErrorException): Long =
        runCatching {
            objectMapper.readTree(ex.responseBodyAsString)
                .path("parameters")
                .path("retry_after")
                .asLong(DEFAULT_RETRY_AFTER_SECONDS)
        }.getOrDefault(DEFAULT_RETRY_AFTER_SECONDS)

    private fun Throwable.safeMessage(): String =
        (message ?: javaClass.simpleName).lineSequence().firstOrNull()?.take(240) ?: javaClass.simpleName

    private companion object {
        const val MAX_RETRY_ATTEMPTS = 3
        const val MAX_RETRY_AFTER_SECONDS = 60L
        const val DEFAULT_RETRY_AFTER_SECONDS = 5L
    }
}

private fun JsonNode.textOrNull(): String? =
    takeUnless { it.isMissingNode || it.isNull }?.asString()

private fun JsonNode.textOrDefault(defaultValue: String): String =
    textOrNull() ?: defaultValue
