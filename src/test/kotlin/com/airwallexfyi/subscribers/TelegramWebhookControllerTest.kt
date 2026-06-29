package com.airwallexfyi.subscribers

import com.airwallexfyi.notifications.TelegramSendResponse
import com.airwallexfyi.notifications.TelegramTransport
import com.airwallexfyi.notifications.TelegramUpdate
import com.airwallexfyi.state.AppStateRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "airwallex-fyi.dry-run=false",
        "airwallex-fyi.telegram.bot-token=test-token",
        "airwallex-fyi.telegram.webhook-secret=test-webhook-secret",
        "spring.datasource.url=jdbc:h2:mem:telegram_webhook_controller_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=true",
    ],
)
@AutoConfigureMockMvc
class TelegramWebhookControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val subscriberRepository: SubscriberRepository,
    private val subscriberChannelRepository: SubscriberChannelRepository,
    private val appStateRepository: AppStateRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val telegramTransport: RecordingTelegramTransport,
) {
    @BeforeEach
    fun clearData() {
        subscriberChannelRepository.deleteAll()
        subscriberRepository.deleteAll()
        jdbcTemplate.update("DELETE FROM app_state")
        telegramTransport.sentBodies.clear()
    }

    @Test
    fun `valid webhook start subscribes chat and sends confirmation`() {
        mockMvc.perform(
            post("/telegram/webhook")
                .header(TelegramWebhookController.SECRET_HEADER, "test-webhook-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("/start", 123456789, "henry")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.processed").value(true))
            .andExpect(jsonPath("$.subscribed").value(1))
            .andExpect(jsonPath("$.failed").value(0))

        val channel = subscriberChannelRepository.findByChannelAndRecipient(
            SubscriberChannelType.TELEGRAM,
            "123456789",
        )
        assertThat(channel?.status).isEqualTo(SubscriberStatus.ACTIVE)
        assertThat(subscriberRepository.findAll().single().displayName).isEqualTo("@henry")
        assertThat(telegramTransport.sentBodies.single()).contains("subscribed")
        assertThat(appStateRepository.findValue("telegram.last_update_id")).isEqualTo("9001")
    }

    @Test
    fun `missing webhook secret is rejected`() {
        mockMvc.perform(
            post("/telegram/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("/start", 123456789, "henry")),
        )
            .andExpect(status().isUnauthorized)

        assertThat(subscriberChannelRepository.count()).isZero()
        assertThat(telegramTransport.sentBodies).isEmpty()
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TelegramWebhookTestConfig {
        @Bean
        @Primary
        fun recordingTelegramTransport(): RecordingTelegramTransport = RecordingTelegramTransport()
    }

    private fun updateJson(text: String, chatId: Long, username: String): String =
        """
        {
          "update_id": 9001,
          "message": {
            "text": "$text",
            "chat": {
              "id": $chatId,
              "username": "$username",
              "first_name": "Test",
              "last_name": "User"
            }
          }
        }
        """.trimIndent()
}

class RecordingTelegramTransport : TelegramTransport {
    val sentBodies = mutableListOf<String>()

    override fun sendMessage(botToken: String, chatId: String, body: String): TelegramSendResponse {
        sentBodies += body
        return TelegramSendResponse("message-${sentBodies.size}")
    }

    override fun getUpdates(botToken: String, offset: Long?): List<TelegramUpdate> = emptyList()
}
