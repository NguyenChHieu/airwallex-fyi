package com.airwallexfyi.notifications

import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.posts.PostRecord
import java.time.Instant
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.ObjectMapper

@Component
@ConditionalOnProperty(prefix = "airwallex-fyi", name = ["dry-run"], havingValue = "false")
class TwilioWhatsAppNotifier(
    private val properties: AppProperties,
    private val notificationAttemptRepository: NotificationAttemptRepository,
    private val transport: TwilioTransport,
) : WhatsAppNotifier {
    override fun send(post: PostRecord, payload: WhatsAppAlertPayload): NotificationResult {
        val now = Instant.now()
        return try {
            val response = transport.sendMessage(
                accountSid = properties.twilio.accountSid,
                authToken = properties.twilio.authToken,
                from = properties.twilio.whatsappFrom,
                to = payload.recipient,
                body = payload.body,
            )
            notificationAttemptRepository.save(
                NotificationAttemptRecord(
                    postId = post.identifier(),
                    channel = payload.channel,
                    recipient = payload.recipient,
                    status = NotificationStatus.SENT.name,
                    providerMessageId = response.sid,
                    attemptedAt = now,
                    sentAt = Instant.now(),
                    createdAt = now,
                    updatedAt = Instant.now(),
                ),
            )
            NotificationResult(
                status = NotificationStatus.SENT,
                payloadPreview = payload.preview,
                providerMessageId = response.sid,
                twilioCalled = true,
            )
        } catch (ex: RuntimeException) {
            val reason = ex.sanitizedReason(properties.twilio.authToken)
            notificationAttemptRepository.save(
                NotificationAttemptRecord(
                    postId = post.identifier(),
                    channel = payload.channel,
                    recipient = payload.recipient,
                    status = NotificationStatus.FAILED.name,
                    errorMessage = reason,
                    attemptedAt = now,
                    createdAt = now,
                    updatedAt = Instant.now(),
                ),
            )
            NotificationResult(
                status = NotificationStatus.FAILED,
                payloadPreview = payload.preview,
                errorMessage = reason,
                twilioCalled = true,
            )
        }
    }

    private fun Throwable.sanitizedReason(secret: String): String {
        val raw = (message ?: javaClass.simpleName).lineSequence().firstOrNull()?.take(ERROR_LIMIT) ?: javaClass.simpleName
        return if (secret.isBlank()) raw else raw.replace(secret, "[redacted]")
    }

    private companion object {
        const val ERROR_LIMIT = 240
    }
}

interface TwilioTransport {
    fun sendMessage(accountSid: String, authToken: String, from: String, to: String, body: String): TwilioSendResponse
}

data class TwilioSendResponse(val sid: String)

@Component
class RestClientTwilioTransport(
    private val objectMapper: ObjectMapper,
) : TwilioTransport {
    private val restClient = RestClient.builder()
        .baseUrl("https://api.twilio.com")
        .build()

    override fun sendMessage(accountSid: String, authToken: String, from: String, to: String, body: String): TwilioSendResponse {
        require(accountSid.isNotBlank()) { "Twilio account SID is not configured" }
        require(authToken.isNotBlank()) { "Twilio auth token is not configured" }
        require(from.isNotBlank()) { "Twilio WhatsApp sender is not configured" }
        require(to.isNotBlank()) { "WhatsApp recipient is not configured" }

        val form = LinkedMultiValueMap<String, String>().apply {
            add("From", from)
            add("To", to)
            add("Body", body)
        }

        val responseBody = try {
            restClient.post()
                .uri("/2010-04-01/Accounts/{accountSid}/Messages.json", accountSid)
                .headers { headers -> headers.setBasicAuth(accountSid, authToken) }
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String::class.java)
                ?: throw IllegalStateException("Twilio returned an empty response body")
        } catch (ex: RestClientException) {
            throw IllegalStateException("Twilio request failed: ${ex.safeMessage()}", ex)
        }

        val sid = objectMapper.readTree(responseBody).path("sid").asText(null).orEmpty()
        if (sid.isBlank()) {
            throw IllegalStateException("Twilio response did not include a message SID")
        }
        return TwilioSendResponse(sid = sid)
    }

    private fun Throwable.safeMessage(): String =
        (message ?: javaClass.simpleName).lineSequence().firstOrNull()?.take(240) ?: javaClass.simpleName
}
