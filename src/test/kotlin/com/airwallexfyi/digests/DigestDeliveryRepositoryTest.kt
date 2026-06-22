package com.airwallexfyi.digests

import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import com.airwallexfyi.posts.ProcessingStatus
import com.airwallexfyi.subscribers.SubscriberChannelRecord
import com.airwallexfyi.subscribers.SubscriberChannelRepository
import com.airwallexfyi.subscribers.SubscriberChannelType
import com.airwallexfyi.subscribers.SubscriberRecord
import com.airwallexfyi.subscribers.SubscriberRepository
import com.airwallexfyi.subscribers.SubscriberStatus
import com.airwallexfyi.summaries.SummaryRecord
import com.airwallexfyi.summaries.SummaryRepository
import java.time.Instant
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:digest_delivery_repository_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=true",
    ],
)
class DigestDeliveryRepositoryTest @Autowired constructor(
    private val subscriberRepository: SubscriberRepository,
    private val subscriberChannelRepository: SubscriberChannelRepository,
    private val postRepository: PostRepository,
    private val summaryRepository: SummaryRepository,
    private val digestDeliveryRepository: DigestDeliveryRepository,
    private val digestDeliveryPostRepository: DigestDeliveryPostRepository,
    private val digestEligibilityService: DigestEligibilityService,
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
    fun `saves no change delivery without post links`() {
        val channel = createChannel("whatsapp:+15550002001")
        val delivery = digestDeliveryRepository.save(
            DigestDeliveryRecord(
                subscriberChannelId = channel.identifier(),
                localDate = LocalDate.of(2026, 6, 22),
                messageType = DigestMessageType.NO_CHANGES,
                status = DigestDeliveryStatus.DRY_RUN,
                recipient = channel.recipient,
                channel = channel.channel,
                payloadPreview = "Airwallex FYI: No new public Blog or Newsroom updates today.",
                attemptedAt = Instant.parse("2026-06-22T00:00:00Z"),
                sentAt = Instant.parse("2026-06-22T00:00:00Z"),
            ),
        )

        val found = digestDeliveryRepository.findBySubscriberChannelIdAndLocalDate(
            channel.identifier(),
            LocalDate.of(2026, 6, 22),
        )

        assertThat(found?.identifier()).isEqualTo(delivery.identifier())
        assertThat(found?.messageType).isEqualTo(DigestMessageType.NO_CHANGES)
        assertThat(digestDeliveryPostRepository.findByDigestDeliveryIdOrderByDisplayOrderAsc(delivery.identifier())).isEmpty()
    }

    @Test
    fun `saves digest delivery with canonical post summary links`() {
        val channel = createChannel("whatsapp:+15550002002")
        val first = createSummarizedPost(
            url = "https://www.airwallex.com/global/blog/digest-first-${System.nanoTime()}",
            summaryCreatedAt = Instant.parse("2026-06-22T01:00:00Z"),
        )
        val second = createSummarizedPost(
            url = "https://www.airwallex.com/global/blog/digest-second-${System.nanoTime()}",
            summaryCreatedAt = Instant.parse("2026-06-22T02:00:00Z"),
        )
        val delivery = digestDeliveryRepository.save(
            DigestDeliveryRecord(
                subscriberChannelId = channel.identifier(),
                localDate = LocalDate.of(2026, 6, 22),
                messageType = DigestMessageType.DIGEST,
                status = DigestDeliveryStatus.SENT,
                recipient = channel.recipient,
                channel = channel.channel,
                payloadPreview = "Airwallex FYI digest",
                providerMessageId = "SM123",
                attemptedAt = Instant.parse("2026-06-22T03:00:00Z"),
                sentAt = Instant.parse("2026-06-22T03:00:01Z"),
            ),
        )

        digestDeliveryPostRepository.save(
            DigestDeliveryPostRecord(
                digestDeliveryId = delivery.identifier(),
                postId = first.post.identifier(),
                summaryId = first.summary.identifier(),
                displayOrder = 0,
            ),
        )
        digestDeliveryPostRepository.save(
            DigestDeliveryPostRecord(
                digestDeliveryId = delivery.identifier(),
                postId = second.post.identifier(),
                summaryId = second.summary.identifier(),
                displayOrder = 1,
            ),
        )

        val links = digestDeliveryPostRepository.findByDigestDeliveryIdOrderByDisplayOrderAsc(delivery.identifier())

        assertThat(links.map { it.postId }).containsExactly(first.post.identifier(), second.post.identifier())
        assertThat(links.map { it.summaryId }).containsExactly(first.summary.identifier(), second.summary.identifier())
    }

    @Test
    fun `rejects duplicate local date deliveries for one subscriber channel`() {
        val channel = createChannel("whatsapp:+15550002003")
        val localDate = LocalDate.of(2026, 6, 22)

        digestDeliveryRepository.save(createDelivery(channel, localDate, DigestDeliveryStatus.DRY_RUN))

        assertThatThrownBy {
            digestDeliveryRepository.save(createDelivery(channel, localDate, DigestDeliveryStatus.SENT))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `finds most recent successful delivery and ignores failures`() {
        val channel = createChannel("whatsapp:+15550002004")
        val dryRun = digestDeliveryRepository.save(
            createDelivery(
                channel = channel,
                localDate = LocalDate.of(2026, 6, 21),
                status = DigestDeliveryStatus.DRY_RUN,
                attemptedAt = Instant.parse("2026-06-21T09:00:00Z"),
            ),
        )
        digestDeliveryRepository.save(
            createDelivery(
                channel = channel,
                localDate = LocalDate.of(2026, 6, 22),
                status = DigestDeliveryStatus.FAILED,
                attemptedAt = Instant.parse("2026-06-22T10:00:00Z"),
                sentAt = null,
            ),
        )

        val recent = digestDeliveryRepository.findMostRecentSuccessfulDelivery(channel.identifier())

        assertThat(recent?.identifier()).isEqualTo(dryRun.identifier())
    }

    @Test
    fun `eligibility returns summary ready posts after the supplied lower bound`() {
        val old = createSummarizedPost(
            url = "https://www.airwallex.com/global/blog/old-${System.nanoTime()}",
            summaryCreatedAt = Instant.parse("2026-06-22T09:00:00Z"),
        )
        val fresh = createSummarizedPost(
            url = "https://www.airwallex.com/global/blog/fresh-${System.nanoTime()}",
            summaryCreatedAt = Instant.parse("2026-06-22T11:00:00Z"),
        )
        createSummarizedPost(
            url = "https://www.airwallex.com/global/blog/seeded-${System.nanoTime()}",
            status = ProcessingStatus.SEEDED.name,
            summaryCreatedAt = Instant.parse("2026-06-22T12:00:00Z"),
        )
        createSummarizedPost(
            url = "https://www.airwallex.com/global/blog/approval-${System.nanoTime()}",
            status = ProcessingStatus.APPROVAL_NEEDED.name,
            summaryCreatedAt = Instant.parse("2026-06-22T13:00:00Z"),
        )

        val eligible = digestEligibilityService.findEligibleSummariesSince(Instant.parse("2026-06-22T10:00:00Z"))

        assertThat(eligible.map { it.summary.identifier() }).containsExactly(fresh.summary.identifier())
        assertThat(eligible.map { it.summary.identifier() }).doesNotContain(old.summary.identifier())
    }

    @Test
    fun `eligibility can pick up posts summarized after a previous no change delivery`() {
        val channel = createChannel("whatsapp:+15550002005")
        val noChange = digestDeliveryRepository.save(
            createDelivery(
                channel = channel,
                localDate = LocalDate.of(2026, 6, 22),
                status = DigestDeliveryStatus.DRY_RUN,
                attemptedAt = Instant.parse("2026-06-22T09:00:00Z"),
            ),
        )
        val summarizedLater = createSummarizedPost(
            url = "https://www.airwallex.com/global/blog/after-no-change-${System.nanoTime()}",
            summaryCreatedAt = Instant.parse("2026-06-22T10:00:00Z"),
        )

        val eligible = digestEligibilityService.findEligibleSummariesSince(noChange.sentAt ?: noChange.attemptedAt)

        assertThat(eligible.map { it.summary.identifier() }).containsExactly(summarizedLater.summary.identifier())
    }

    private fun createChannel(recipient: String): SubscriberChannelRecord {
        val now = Instant.parse("2026-06-22T00:00:00Z")
        val subscriber = subscriberRepository.save(
            SubscriberRecord(displayName = "Subscriber $recipient", createdAt = now, updatedAt = now),
        )
        return subscriberChannelRepository.save(
            SubscriberChannelRecord(
                subscriberId = subscriber.identifier(),
                channel = SubscriberChannelType.WHATSAPP,
                recipient = recipient,
                status = SubscriberStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun createDelivery(
        channel: SubscriberChannelRecord,
        localDate: LocalDate,
        status: String,
        attemptedAt: Instant = Instant.parse("2026-06-22T00:00:00Z"),
        sentAt: Instant? = attemptedAt,
    ): DigestDeliveryRecord = DigestDeliveryRecord(
        subscriberChannelId = channel.identifier(),
        localDate = localDate,
        messageType = DigestMessageType.NO_CHANGES,
        status = status,
        recipient = channel.recipient,
        channel = channel.channel,
        payloadPreview = "Airwallex FYI: No new public Blog or Newsroom updates today.",
        attemptedAt = attemptedAt,
        sentAt = sentAt,
        createdAt = attemptedAt,
        updatedAt = attemptedAt,
    )

    private fun createSummarizedPost(
        url: String,
        status: String = ProcessingStatus.SUMMARY_READY.name,
        summaryCreatedAt: Instant,
    ): SummarizedPostFixture {
        val post = postRepository.save(
            PostRecord(
                url = url,
                sourceType = "BLOG",
                title = "Airwallex update",
                discoveredAt = summaryCreatedAt.minusSeconds(60),
                processingStatus = status,
                createdAt = summaryCreatedAt.minusSeconds(60),
                updatedAt = summaryCreatedAt,
            ),
        )
        val summary = summaryRepository.save(
            SummaryRecord(
                postId = post.identifier(),
                headline = "Headline for ${post.identifier()}",
                summaryJson = """
                {"headline":"Headline","bullets":["One useful point"],"why_it_matters":"It matters","tags":["payments"],"source_type":"BLOG"}
                """.trimIndent(),
                whyItMatters = "It matters",
                tagsJson = "[\"payments\"]",
                model = "test-model",
                promptVersion = "test-prompt",
                createdAt = summaryCreatedAt,
                updatedAt = summaryCreatedAt,
            ),
        )
        return SummarizedPostFixture(post = post, summary = summary)
    }

    private data class SummarizedPostFixture(
        val post: PostRecord,
        val summary: SummaryRecord,
    )
}
