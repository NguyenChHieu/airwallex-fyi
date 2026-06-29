package com.airwallexfyi.digests

import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.notifications.NotificationResult
import com.airwallexfyi.notifications.NotificationStatus
import com.airwallexfyi.notifications.TelegramNotifier
import com.airwallexfyi.notifications.WhatsAppAlertPayload
import com.airwallexfyi.notifications.WhatsAppNotifier
import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.posts.SourceType
import com.airwallexfyi.subscribers.SubscriberChannelRecord
import com.airwallexfyi.subscribers.SubscriberChannelRepository
import com.airwallexfyi.subscribers.SubscriberChannelType
import com.airwallexfyi.subscribers.SubscriberRecord
import com.airwallexfyi.subscribers.SubscriberRepository
import com.airwallexfyi.subscribers.SubscriberStatus
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
import tools.jackson.databind.ObjectMapper

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:daily_digest_service_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=true",
    ],
)
class DailyDigestServiceTest @Autowired constructor(
    private val subscriberRepository: SubscriberRepository,
    private val subscriberChannelRepository: SubscriberChannelRepository,
    private val postRepository: PostRepository,
    private val summaryRepository: SummaryRepository,
    private val digestDeliveryRepository: DigestDeliveryRepository,
    private val digestDeliveryPostRepository: DigestDeliveryPostRepository,
    private val digestEligibilityService: DigestEligibilityService,
    private val objectMapper: ObjectMapper,
) {
    @BeforeEach
    fun clearData() {
        digestDeliveryPostRepository.deleteAll()
        digestDeliveryRepository.deleteAll()
        summaryRepository.deleteAll()
        postRepository.deleteAll()
        subscriberChannelRepository.deleteAll()
        subscriberRepository.deleteAll()
    }

    @Test
    fun `fans out one combined digest to each active subscriber channel`() {
        val firstChannel = createChannel("whatsapp:+15550003001")
        val secondChannel = createChannel("whatsapp:+15550003002")
        val summarized = createSummarizedPost("https://www.airwallex.com/global/blog/fanout-${System.nanoTime()}")
        val notifier = FakeWhatsAppNotifier()
        val service = service(notifier)

        val result = service.sendDailyDigests(Instant.parse("2026-06-22T01:00:00Z"))

        assertThat(result.digestSentCount).isEqualTo(2)
        assertThat(result.noChangeCount).isZero()
        assertThat(notifier.payloads.map { it.recipient }).containsExactly(firstChannel.recipient, secondChannel.recipient)
        assertThat(notifier.payloads).allSatisfy { payload ->
            assertThat(payload.body).contains("Airwallex FYI - 2026-06-22")
            assertThat(payload.body).contains("Read: ${summarized.post.url}")
        }
        assertThat(linkedPostIds(firstChannel, LocalDate.of(2026, 6, 22))).containsExactly(summarized.post.identifier())
        assertThat(linkedPostIds(secondChannel, LocalDate.of(2026, 6, 22))).containsExactly(summarized.post.identifier())
    }

    @Test
    fun `sends telegram subscriber digest through telegram notifier`() {
        val channel = createChannel("123456789", SubscriberChannelType.TELEGRAM)
        val summarized = createSummarizedPost("https://www.airwallex.com/global/blog/telegram-${System.nanoTime()}")
        val whatsAppNotifier = FakeWhatsAppNotifier()
        val telegramNotifier = FakeTelegramNotifier()
        val service = service(whatsAppNotifier, telegramNotifier)

        val result = service.sendDailyDigests(Instant.parse("2026-06-22T01:00:00Z"))

        val delivery = requireNotNull(
            digestDeliveryRepository.findBySubscriberChannelIdAndLocalDate(channel.identifier(), LocalDate.of(2026, 6, 22)),
        )
        assertThat(result.digestSentCount).isEqualTo(1)
        assertThat(result.noChangeCount).isZero()
        assertThat(whatsAppNotifier.payloads).isEmpty()
        assertThat(telegramNotifier.payloads.single().recipient).isEqualTo("123456789")
        assertThat(telegramNotifier.payloads.single().body).contains("Read: ${summarized.post.url}")
        assertThat(delivery.channel).isEqualTo(SubscriberChannelType.TELEGRAM)
        assertThat(delivery.status).isEqualTo(DigestDeliveryStatus.DRY_RUN)
    }

    @Test
    fun `sends no change message with delivery row and no post links`() {
        val channel = createChannel("whatsapp:+15550003003")
        val notifier = FakeWhatsAppNotifier()
        val service = service(notifier)

        val result = service.sendDailyDigests(Instant.parse("2026-06-22T01:00:00Z"))

        val delivery = requireNotNull(
            digestDeliveryRepository.findBySubscriberChannelIdAndLocalDate(channel.identifier(), LocalDate.of(2026, 6, 22)),
        )
        assertThat(result.noChangeCount).isEqualTo(1)
        assertThat(result.digestSentCount).isZero()
        assertThat(notifier.payloads.single().body).isEqualTo(DailyDigestFormatter.NO_CHANGES_TEXT)
        assertThat(delivery.messageType).isEqualTo(DigestMessageType.NO_CHANGES)
        assertThat(delivery.status).isEqualTo(DigestDeliveryStatus.DRY_RUN)
        assertThat(digestDeliveryPostRepository.findByDigestDeliveryIdOrderByDisplayOrderAsc(delivery.identifier())).isEmpty()
    }

    @Test
    fun `second run on same local date skips duplicate sends`() {
        val channel = createChannel("whatsapp:+15550003004")
        createSummarizedPost("https://www.airwallex.com/global/blog/duplicate-guard-${System.nanoTime()}")
        val notifier = FakeWhatsAppNotifier()
        val service = service(notifier)

        service.sendDailyDigests(Instant.parse("2026-06-22T01:00:00Z"))
        val second = service.sendDailyDigests(Instant.parse("2026-06-22T05:00:00Z"))

        assertThat(second.skippedDuplicateCount).isEqualTo(1)
        assertThat(second.digestSentCount).isZero()
        assertThat(notifier.payloads).hasSize(1)
        assertThat(
            digestDeliveryRepository.findBySubscriberChannelIdAndLocalDate(channel.identifier(), LocalDate.of(2026, 6, 22)),
        ).isNotNull
    }

    @Test
    fun `service timezone controls delivery local date`() {
        val channel = createChannel("whatsapp:+15550003005")
        val notifier = FakeWhatsAppNotifier()
        val service = service(
            notifier = notifier,
            properties = AppProperties(digest = AppProperties.Digest(timeZone = "America/Los_Angeles")),
        )

        service.sendDailyDigests(Instant.parse("2026-06-22T01:00:00Z"))

        assertThat(
            digestDeliveryRepository.findBySubscriberChannelIdAndLocalDate(channel.identifier(), LocalDate.of(2026, 6, 21)),
        ).isNotNull
        assertThat(
            digestDeliveryRepository.findBySubscriberChannelIdAndLocalDate(channel.identifier(), LocalDate.of(2026, 6, 22)),
        ).isNull()
    }

    @Test
    fun `failed subscriber channel retries without blocking other channels`() {
        val failingChannel = createChannel("whatsapp:+15550003006")
        val successfulChannel = createChannel("whatsapp:+15550003007")
        createSummarizedPost("https://www.airwallex.com/global/blog/failure-isolated-${System.nanoTime()}")
        val notifier = FakeWhatsAppNotifier(failingRecipients = setOf(failingChannel.recipient))
        val service = service(notifier)

        val result = service.sendDailyDigests(Instant.parse("2026-06-22T01:00:00Z"))
        val retry = service.sendDailyDigests(Instant.parse("2026-06-22T03:00:00Z"))

        val failedDelivery = requireNotNull(
            digestDeliveryRepository.findBySubscriberChannelIdAndLocalDate(failingChannel.identifier(), LocalDate.of(2026, 6, 22)),
        )
        val successfulDelivery = requireNotNull(
            digestDeliveryRepository.findBySubscriberChannelIdAndLocalDate(successfulChannel.identifier(), LocalDate.of(2026, 6, 22)),
        )
        assertThat(result.failedCount).isEqualTo(1)
        assertThat(result.digestSentCount).isEqualTo(1)
        assertThat(failedDelivery.status).isEqualTo(DigestDeliveryStatus.FAILED)
        assertThat(failedDelivery.errorMessage).contains("simulated failure")
        assertThat(successfulDelivery.status).isEqualTo(DigestDeliveryStatus.DRY_RUN)
        assertThat(retry.failedCount).isEqualTo(1)
        assertThat(retry.skippedDuplicateCount).isEqualTo(1)
        assertThat(notifier.payloads).hasSize(3)
    }

    @Test
    fun `skipped whatsapp delivery is not counted as failure or sent digest`() {
        val channel = createChannel("whatsapp:+15550003008")
        createSummarizedPost("https://www.airwallex.com/global/blog/skipped-whatsapp-${System.nanoTime()}")
        val notifier = FakeWhatsAppNotifier(skippedRecipients = setOf(channel.recipient))
        val service = service(notifier)

        val result = service.sendDailyDigests(Instant.parse("2026-06-22T01:00:00Z"))

        val delivery = requireNotNull(
            digestDeliveryRepository.findBySubscriberChannelIdAndLocalDate(channel.identifier(), LocalDate.of(2026, 6, 22)),
        )
        assertThat(result.failedCount).isZero()
        assertThat(result.digestSentCount).isZero()
        assertThat(result.noChangeCount).isZero()
        assertThat(delivery.status).isEqualTo(DigestDeliveryStatus.SKIPPED)
        assertThat(delivery.sentAt).isNull()
    }

    private fun service(
        notifier: FakeWhatsAppNotifier,
        telegramNotifier: FakeTelegramNotifier = FakeTelegramNotifier(),
        properties: AppProperties = AppProperties(),
    ): DailyDigestService = DailyDigestService(
        properties = properties,
        subscriberChannelRepository = subscriberChannelRepository,
        digestDeliveryRepository = digestDeliveryRepository,
        digestDeliveryPostRepository = digestDeliveryPostRepository,
        digestEligibilityService = digestEligibilityService,
        dailyDigestFormatter = DailyDigestFormatter(objectMapper),
        whatsAppNotifier = notifier,
        telegramNotifier = telegramNotifier,
    )

    private fun createChannel(recipient: String, channel: String = SubscriberChannelType.WHATSAPP): SubscriberChannelRecord {
        val now = Instant.parse("2026-06-22T00:00:00Z")
        val subscriber = subscriberRepository.save(
            SubscriberRecord(displayName = "Subscriber $recipient", createdAt = now, updatedAt = now),
        )
        return subscriberChannelRepository.save(
            SubscriberChannelRecord(
                subscriberId = subscriber.identifier(),
                channel = channel,
                recipient = recipient,
                status = SubscriberStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun createSummarizedPost(url: String): SummarizedFixture {
        val now = Instant.parse("2026-06-22T00:30:00Z")
        val post = postRepository.save(
            PostRecord(
                url = url,
                sourceType = SourceType.BLOG.name,
                title = "Airwallex update",
                discoveredAt = now,
                processingStatus = ProcessingStatus.SUMMARY_READY.name,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val structuredSummary = StructuredSummary.validated(
            headline = "Airwallex ships an update",
            bullets = listOf("First useful point", "Second useful point", "Third useful point"),
            whyItMatters = "It helps track public Airwallex changes.",
            tags = listOf("payments"),
            sourceType = SourceType.BLOG,
        )
        val summary = summaryRepository.save(
            SummaryRecord.from(
                postId = post.identifier(),
                summary = structuredSummary,
                model = "test-model",
                promptVersion = "test-prompt",
                objectMapper = objectMapper,
                now = now.plusSeconds(60),
            ),
        )
        return SummarizedFixture(post, summary)
    }

    private fun linkedPostIds(channel: SubscriberChannelRecord, localDate: LocalDate): List<java.util.UUID> {
        val delivery = requireNotNull(digestDeliveryRepository.findBySubscriberChannelIdAndLocalDate(channel.identifier(), localDate))
        return digestDeliveryPostRepository.findByDigestDeliveryIdOrderByDisplayOrderAsc(delivery.identifier())
            .map { it.postId }
    }

    private class FakeWhatsAppNotifier(
        private val failingRecipients: Set<String> = emptySet(),
        private val skippedRecipients: Set<String> = emptySet(),
    ) : WhatsAppNotifier {
        val payloads: MutableList<WhatsAppAlertPayload> = mutableListOf()

        override fun send(payload: WhatsAppAlertPayload): NotificationResult {
            payloads += payload
            if (payload.recipient in failingRecipients) {
                return NotificationResult(
                    status = NotificationStatus.FAILED,
                    payloadPreview = payload.preview,
                    errorMessage = "simulated failure for ${payload.recipient}",
                    twilioCalled = false,
                )
            }
            if (payload.recipient in skippedRecipients) {
                return NotificationResult(
                    status = NotificationStatus.SKIPPED,
                    payloadPreview = payload.preview,
                    errorMessage = "simulated skip for ${payload.recipient}",
                    twilioCalled = false,
                )
            }
            return NotificationResult(
                status = NotificationStatus.DRY_RUN,
                payloadPreview = payload.preview,
                twilioCalled = false,
            )
        }
    }

    private class FakeTelegramNotifier : TelegramNotifier {
        val payloads: MutableList<WhatsAppAlertPayload> = mutableListOf()

        override fun send(payload: WhatsAppAlertPayload): NotificationResult {
            payloads += payload
            return NotificationResult(
                status = NotificationStatus.DRY_RUN,
                payloadPreview = payload.preview,
                twilioCalled = false,
            )
        }
    }

    private data class SummarizedFixture(
        val post: PostRecord,
        val summary: SummaryRecord,
    )
}
