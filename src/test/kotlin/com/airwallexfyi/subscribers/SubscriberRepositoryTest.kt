package com.airwallexfyi.subscribers

import com.airwallexfyi.config.AppProperties
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:subscriber_repository_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=true",
        "airwallex-fyi.whatsapp.to=whatsapp:+15550000002",
    ],
)
class SubscriberRepositoryTest @Autowired constructor(
    private val subscriberRepository: SubscriberRepository,
    private val subscriberChannelRepository: SubscriberChannelRepository,
    private val subscriberSeedService: SubscriberSeedService,
) {
    @BeforeEach
    fun clearSubscribers() {
        subscriberChannelRepository.deleteAll()
        subscriberRepository.deleteAll()
    }

    @Test
    fun `saves subscriber channels and finds active whatsapp recipients`() {
        val now = Instant.parse("2026-06-22T00:00:00Z")
        val activeSubscriber = subscriberRepository.save(
            SubscriberRecord(displayName = "Active", status = SubscriberStatus.ACTIVE, createdAt = now, updatedAt = now),
        )
        val inactiveSubscriber = subscriberRepository.save(
            SubscriberRecord(displayName = "Inactive", status = SubscriberStatus.INACTIVE, createdAt = now, updatedAt = now),
        )
        val activeChannel = subscriberChannelRepository.save(
            SubscriberChannelRecord(
                subscriberId = activeSubscriber.identifier(),
                channel = SubscriberChannelType.WHATSAPP,
                recipient = "whatsapp:+15550001001",
                status = SubscriberStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            ),
        )
        subscriberChannelRepository.save(
            SubscriberChannelRecord(
                subscriberId = inactiveSubscriber.identifier(),
                channel = SubscriberChannelType.WHATSAPP,
                recipient = "whatsapp:+15550001002",
                status = SubscriberStatus.INACTIVE,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val activeChannels = subscriberChannelRepository.findByChannelAndStatusOrderByCreatedAtAsc(
            SubscriberChannelType.WHATSAPP,
            SubscriberStatus.ACTIVE,
        )

        assertThat(activeChannels.map { it.identifier() }).containsExactly(activeChannel.identifier())
        assertThat(
            subscriberChannelRepository.findByChannelAndRecipient(
                SubscriberChannelType.WHATSAPP,
                "whatsapp:+15550001001",
            )?.subscriberId,
        ).isEqualTo(activeSubscriber.identifier())
    }

    @Test
    fun `rejects duplicate channel recipient pairs`() {
        val subscriber = subscriberRepository.save(SubscriberRecord(displayName = "Primary"))
        val recipient = "whatsapp:+1555${System.nanoTime()}"

        subscriberChannelRepository.save(
            SubscriberChannelRecord(
                subscriberId = subscriber.identifier(),
                channel = SubscriberChannelType.WHATSAPP,
                recipient = recipient,
            ),
        )

        assertThatThrownBy {
            subscriberChannelRepository.save(
                SubscriberChannelRecord(
                    subscriberId = subscriber.identifier(),
                    channel = SubscriberChannelType.WHATSAPP,
                    recipient = recipient,
                ),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `seed service creates default subscriber channel once from config`() {
        val now = Instant.parse("2026-06-22T01:00:00Z")

        val created = subscriberSeedService.seedDefaultSubscriberIfConfigured(now)

        assertThat(created).isTrue()
        assertThat(subscriberRepository.count()).isEqualTo(1)
        assertThat(subscriberChannelRepository.count()).isEqualTo(1)
        val channel = subscriberChannelRepository.findByChannelAndRecipient(
            SubscriberChannelType.WHATSAPP,
            "whatsapp:+15550000002",
        )
        assertThat(channel?.status).isEqualTo(SubscriberStatus.ACTIVE)
    }

    @Test
    fun `seed service is idempotent and does not overwrite matching channel`() {
        val first = subscriberSeedService.seedDefaultSubscriberIfConfigured(Instant.parse("2026-06-22T01:00:00Z"))
        val second = subscriberSeedService.seedDefaultSubscriberIfConfigured(Instant.parse("2026-06-23T01:00:00Z"))

        assertThat(first).isTrue()
        assertThat(second).isFalse()
        assertThat(subscriberRepository.count()).isEqualTo(1)
        assertThat(subscriberChannelRepository.count()).isEqualTo(1)
    }

    @Test
    fun `blank whatsapp config does not seed invalid rows`() {
        val blankSeedService = SubscriberSeedService(
            AppProperties(whatsapp = AppProperties.WhatsApp(to = "   ")),
            subscriberRepository,
            subscriberChannelRepository,
        )

        val created = blankSeedService.seedDefaultSubscriberIfConfigured()

        assertThat(created).isFalse()
        assertThat(subscriberRepository.count()).isZero()
        assertThat(subscriberChannelRepository.count()).isZero()
    }
}


