package com.airwallexfyi.subscribers

import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.digests.DigestDeliveryRecord
import com.airwallexfyi.digests.DigestDeliveryRepository
import com.airwallexfyi.digests.DigestDeliveryStatus
import com.airwallexfyi.digests.DigestMessageType
import com.airwallexfyi.digests.LatestUpdatesService
import com.airwallexfyi.notifications.TelegramChat
import com.airwallexfyi.notifications.TelegramMessage
import com.airwallexfyi.notifications.TelegramSendResponse
import com.airwallexfyi.notifications.TelegramTransport
import com.airwallexfyi.notifications.TelegramUpdate
import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.posts.SourceType
import com.airwallexfyi.state.AppStateRepository
import com.airwallexfyi.summaries.StructuredSummary
import com.airwallexfyi.summaries.SummaryRecord
import com.airwallexfyi.summaries.SummaryRepository
import java.time.Instant
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import tools.jackson.databind.ObjectMapper

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
    private val digestDeliveryRepository: DigestDeliveryRepository,
    private val postRepository: PostRepository,
    private val summaryRepository: SummaryRepository,
    private val appStateRepository: AppStateRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) {
    @BeforeEach
    fun clearData() {
        digestDeliveryRepository.deleteAll()
        subscriberChannelRepository.deleteAll()
        subscriberRepository.deleteAll()
        summaryRepository.deleteAll()
        postRepository.deleteAll()
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
    fun `webhook update subscribes immediately and is not replayed by polling`() {
        val now = Instant.parse("2026-06-27T00:00:00Z")
        val transport = FakeTelegramTransport()
        val service = service(transport)

        val result = service.processWebhookUpdate(update(200, "/start", 123456789, username = "henry"), now)
        val duplicate = service.processWebhookUpdate(update(200, "/start", 123456789, username = "henry"), now.plusSeconds(1))
        val pollReplay = service.syncSubscriptions(now.plusSeconds(2))

        val channel = subscriberChannelRepository.findByChannelAndRecipient(
            SubscriberChannelType.TELEGRAM,
            "123456789",
        )
        assertThat(result.processedCount).isEqualTo(1)
        assertThat(result.subscribedCount).isEqualTo(1)
        assertThat(duplicate.processedCount).isZero()
        assertThat(pollReplay.processedCount).isZero()
        assertThat(channel?.status).isEqualTo(SubscriberStatus.ACTIVE)
        assertThat(transport.sentBodies).containsExactly("You're subscribed to Airwallex FYI. Send /stop anytime to unsubscribe.")
        assertThat(transport.offsets).containsExactly(201)
        assertThat(appStateRepository.findValue("telegram.last_update_id")).isEqualTo("200")
    }

    @Test
    fun `latest replies with recent summarized updates without subscribing`() {
        val older = createSummarizedPost(
            slug = "older-update",
            headline = "Older Airwallex update",
            now = Instant.parse("2026-06-26T00:00:00Z"),
        )
        val newer = createSummarizedPost(
            slug = "newer-update",
            headline = "Newer Airwallex update",
            now = Instant.parse("2026-06-27T00:00:00Z"),
        )
        val transport = FakeTelegramTransport(
            updates = listOf(update(201, "/latest", 123456789, username = "henry")),
        )
        val service = service(transport)

        val result = service.syncSubscriptions(Instant.parse("2026-06-28T00:00:00Z"))

        val body = transport.sentBodies.single()
        assertThat(result.processedCount).isEqualTo(1)
        assertThat(result.subscribedCount).isZero()
        assertThat(subscriberChannelRepository.count()).isZero()
        assertThat(body).contains("Airwallex FYI - latest updates")
        assertThat(body).contains("1. Newer Airwallex update")
        assertThat(body).contains("2. Older Airwallex update")
        assertThat(body).contains("Read: ${newer.url}")
        assertThat(body).contains("Read: ${older.url}")
        assertThat(appStateRepository.findValue("telegram.last_update_id")).isEqualTo("201")
    }

    @Test
    fun `status replies with subscription digest and latest post state`() {
        val now = Instant.parse("2026-06-27T00:00:00Z")
        val channel = createTelegramChannel("123456789", SubscriberStatus.ACTIVE, now)
        val latestPost = createSummarizedPost(
            slug = "status-update",
            headline = "Status Airwallex update",
            now = Instant.parse("2026-06-27T01:00:00Z"),
        )
        digestDeliveryRepository.save(
            DigestDeliveryRecord(
                subscriberChannelId = channel.identifier(),
                localDate = LocalDate.of(2026, 6, 27),
                messageType = DigestMessageType.DIGEST,
                status = DigestDeliveryStatus.SENT,
                recipient = "123456789",
                channel = SubscriberChannelType.TELEGRAM,
                attemptedAt = now.plusSeconds(120),
                sentAt = now.plusSeconds(121),
                createdAt = now.plusSeconds(120),
                updatedAt = now.plusSeconds(121),
            ),
        )
        val transport = FakeTelegramTransport(
            updates = listOf(update(202, "/status", 123456789, username = "henry")),
        )
        val service = service(transport)

        val result = service.syncSubscriptions(Instant.parse("2026-06-28T00:00:00Z"))

        val body = transport.sentBodies.single()
        assertThat(result.processedCount).isEqualTo(1)
        assertThat(result.subscribedCount).isZero()
        assertThat(body).contains("Airwallex FYI status")
        assertThat(body).contains("Subscription: active")
        assertThat(body).contains("Latest digest: SENT DIGEST on 2026-06-27")
        assertThat(body).contains("Latest update seen: ${latestPost.title}")
        assertThat(body).contains("Mode: Render webhook + GitHub Actions daily")
        assertThat(appStateRepository.findValue("telegram.last_update_id")).isEqualTo("202")
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
    fun `configured webhook skips polling to avoid telegram getUpdates conflict`() {
        val transport = FakeTelegramTransport(
            updates = listOf(update(104, "/start", 123456789)),
        )
        val service = service(
            transport,
            AppProperties(
                dryRun = false,
                telegram = AppProperties.Telegram(
                    botToken = "test-token",
                    webhookSecret = "test-webhook-secret",
                ),
            ),
        )

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
        latestUpdatesService = LatestUpdatesService(summaryRepository, postRepository, objectMapper),
        telegramStatusService = TelegramStatusService(
            properties = properties,
            subscriberChannelRepository = subscriberChannelRepository,
            digestDeliveryRepository = digestDeliveryRepository,
            postRepository = postRepository,
        ),
    )

    private fun createTelegramChannel(recipient: String, status: String, now: Instant): SubscriberChannelRecord {
        val subscriber = subscriberRepository.save(
            SubscriberRecord(
                displayName = "Telegram $recipient",
                status = SubscriberStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return subscriberChannelRepository.save(
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

    private fun createSummarizedPost(slug: String, headline: String, now: Instant): PostRecord {
        val url = "https://www.airwallex.com/global/blog/$slug"
        val post = postRepository.save(
            PostRecord(
                url = url,
                sourceType = SourceType.BLOG.name,
                title = headline,
                publishedAt = now,
                discoveredAt = now,
                processingStatus = ProcessingStatus.SUMMARY_READY.name,
                createdAt = now,
                updatedAt = now,
            ),
        )
        summaryRepository.save(
            SummaryRecord.from(
                postId = post.identifier(),
                summary = StructuredSummary.validated(
                    headline = headline,
                    bullets = listOf("First key point", "Second key point", "Third key point"),
                    whyItMatters = "It helps track Airwallex changes.",
                    tags = listOf("payments"),
                    sourceType = SourceType.BLOG,
                ),
                model = "test-model",
                promptVersion = "test-prompt",
                objectMapper = objectMapper,
                now = now.plusSeconds(60),
            ),
        )
        return post
    }

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
