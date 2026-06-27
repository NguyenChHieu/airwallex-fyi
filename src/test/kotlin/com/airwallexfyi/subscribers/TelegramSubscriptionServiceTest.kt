package com.airwallexfyi.subscribers

import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.notifications.TelegramChat
import com.airwallexfyi.notifications.TelegramMessage
import com.airwallexfyi.notifications.TelegramSendResponse
import com.airwallexfyi.notifications.TelegramTransport
import com.airwallexfyi.notifications.TelegramUpdate
import com.airwallexfyi.state.AppStateRepository
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:telegram_subscription_service_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=true",
    ],
)
class TelegramSubscriptionServiceTest @Autowired constructor(
    private val subscriberRepository: SubscriberRepository,
    private val subscriberChannelRepository: SubscriberChannelRepository,
    private val appStateRepository: AppStateRepository,
    private val jdbcTemplate: JdbcTemplate,
) {
    @BeforeEach
    fun clearData() {
        subscriberChannelRepository.deleteAll()
        subscriberRepository.deleteAll()
        jdbcTemplate.update("DELETE FROM app_state")
    }

    @Test
    fun `start subscribes telegram chat and advances update cursor`() {
        val transport = FakeTelegramTransport(
            updates = listOf(update(100, "/start", 123456789, username = "henry")),
        )
        val service = service(transport)

        val result = service.syncSubscriptions(Instant.parse("2026-06-27T00:00:00Z"))
        val replay = service.syncSubscriptions(Instant.parse("2026-06-27T00:01:00Z"))

        val channel = subscriberChannelRepository.findByChannelAndRecipient(
            SubscriberChannelType.TELEGRAM,
            "123456789",
        )
        assertThat(result.processedCount).isEqualTo(1)
        assertThat(result.subscribedCount).isEqualTo(1)
        assertThat(channel?.status).isEqualTo(SubscriberStatus.ACTIVE)
        assertThat(subscriberRepository.findAll().single().displayName).isEqualTo("@henry")
        assertThat(transport.sentBodies.single()).contains("subscribed")
        assertThat(appStateRepository.findValue("telegram.last_update_id")).isEqualTo("100")
        assertThat(replay.processedCount).isZero()
        assertThat(transport.offsets).containsExactly(null, 101)
    }

    @Test
    fun `stop deactivates active telegram channel`() {
        val now = Instant.parse("2026-06-27T00:00:00Z")
        createTelegramChannel("123456789", SubscriberStatus.ACTIVE, now)
        val transport = FakeTelegramTransport(
            updates = listOf(update(101, "/stop", 123456789)),
        )
        val service = service(transport)

        val result = service.syncSubscriptions(now.plusSeconds(60))

        val channel = subscriberChannelRepository.findByChannelAndRecipient(
            SubscriberChannelType.TELEGRAM,
            "123456789",
        )
        assertThat(result.unsubscribedCount).isEqualTo(1)
        assertThat(channel?.status).isEqualTo(SubscriberStatus.INACTIVE)
        assertThat(transport.sentBodies.single()).contains("unsubscribed")
        assertThat(appStateRepository.findValue("telegram.last_update_id")).isEqualTo("101")
    }

    @Test
    fun `start reactivates inactive telegram channel without duplicate subscriber`() {
        val now = Instant.parse("2026-06-27T00:00:00Z")
        createTelegramChannel("123456789", SubscriberStatus.INACTIVE, now)
        val transport = FakeTelegramTransport(
            updates = listOf(update(102, "/start", 123456789)),
        )
        val service = service(transport)

        val result = service.syncSubscriptions(now.plusSeconds(60))

        val channel = subscriberChannelRepository.findByChannelAndRecipient(
            SubscriberChannelType.TELEGRAM,
            "123456789",
        )
        assertThat(result.subscribedCount).isEqualTo(1)
        assertThat(channel?.status).isEqualTo(SubscriberStatus.ACTIVE)
        assertThat(subscriberRepository.count()).isEqualTo(1)
    }

    @Test
    fun `dry run skips telegram network calls`() {
        val transport = FakeTelegramTransport(
            updates = listOf(update(103, "/start", 123456789)),
        )
        val service = service(transport, AppProperties(dryRun = true))

        val result = service.syncSubscriptions()

        assertThat(result.skipped).isTrue()
        assertThat(transport.offsets).isEmpty()
        assertThat(subscriberChannelRepository.count()).isZero()
    }

    @Test
    fun `get updates failure is reported without advancing cursor`() {
        val transport = FakeTelegramTransport(failure = IllegalStateException("telegram down"))
        val service = service(transport)

        val result = service.syncSubscriptions()

        assertThat(result.failedCount).isEqualTo(1)
        assertThat(result.sampleErrors.single()).contains("telegram down")
        assertThat(appStateRepository.findValue("telegram.last_update_id")).isNull()
    }

    private fun service(
        transport: FakeTelegramTransport,
        properties: AppProperties = AppProperties(
            dryRun = false,
            telegram = AppProperties.Telegram(botToken = "test-token"),
        ),
    ): TelegramSubscriptionService = TelegramSubscriptionService(
        properties = properties,
        telegramTransport = transport,
        appStateRepository = appStateRepository,
        subscriberRepository = subscriberRepository,
        subscriberChannelRepository = subscriberChannelRepository,
    )

    private fun createTelegramChannel(recipient: String, status: String, now: Instant) {
        val subscriber = subscriberRepository.save(
            SubscriberRecord(
                displayName = "Telegram $recipient",
                status = SubscriberStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            ),
        )
        subscriberChannelRepository.save(
            SubscriberChannelRecord(
                subscriberId = subscriber.identifier(),
                channel = SubscriberChannelType.TELEGRAM,
                recipient = recipient,
                status = status,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun update(
        updateId: Long,
        text: String,
        chatId: Long,
        username: String? = null,
    ): TelegramUpdate = TelegramUpdate(
        updateId = updateId,
        message = TelegramMessage(
            text = text,
            chat = TelegramChat(
                id = chatId,
                username = username,
                firstName = "Test",
                lastName = "User",
            ),
        ),
    )

    private class FakeTelegramTransport(
        private val updates: List<TelegramUpdate> = emptyList(),
        private val failure: RuntimeException? = null,
    ) : TelegramTransport {
        val offsets = mutableListOf<Long?>()
        val sentBodies = mutableListOf<String>()

        override fun sendMessage(botToken: String, chatId: String, body: String): TelegramSendResponse {
            sentBodies += body
            return TelegramSendResponse("message-${sentBodies.size}")
        }

        override fun getUpdates(botToken: String, offset: Long?): List<TelegramUpdate> {
            offsets += offset
            failure?.let { throw it }
            return updates.filter { update -> offset == null || update.updateId >= offset }
        }
    }
}
