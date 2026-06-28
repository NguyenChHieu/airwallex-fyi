package com.airwallexfyi.notifications

import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.http.RestClientTimeouts
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
    private val transport: TwilioTransport,
) : WhatsAppNotifier {
    override fun send(payload: WhatsAppAlertPayload): NotificationResult {
        if (!properties.twilio.isConfigured()) {
            return NotificationResult(
                status = NotificationStatus.SKIPPED,
                payloadPreview = payload.preview,
                errorMessage = "Twilio WhatsApp is not configured; skipping WhatsApp delivery",
                twilioCalled = false,
            )
        }

        return try {
            val response = transport.sendMessage(
                accountSid = properties.twilio.accountSid,
                authToken = properties.twilio.authToken,
                from = properties.twilio.whatsappFrom,
                to = payload.recipient,
                body = payload.body,
            )
            NotificationResult(
                status = NotificationStatus.SENT,
                payloadPreview = payload.preview,
                providerMessageId = response.sid,
                twilioCalled = true,
            )
        } catch (ex: RuntimeException) {
            val reason = ex.sanitizedReason(properties.twilio.authToken)
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

    private fun AppProperties.Twilio.isConfigured(): Boolean =
        accountSid.isNotBlank() && authToken.isNotBlank() && whatsappFrom.isNotBlank()

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
        .requestFactory(RestClientTimeouts.requestFactory())
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
