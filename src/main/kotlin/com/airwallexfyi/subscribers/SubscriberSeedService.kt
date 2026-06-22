package com.airwallexfyi.subscribers

import com.airwallexfyi.config.AppProperties
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SubscriberSeedService(
    private val properties: AppProperties,
    private val subscriberRepository: SubscriberRepository,
    private val subscriberChannelRepository: SubscriberChannelRepository,
) {
    @Transactional
    fun seedDefaultSubscriberIfConfigured(now: Instant = Instant.now()): SubscriberSeedResult {
        val recipient = properties.whatsapp.to.trim()
        if (recipient.isBlank()) {
            return SubscriberSeedResult(created = false, skippedReason = "blank-recipient")
        }

        val existingChannel = subscriberChannelRepository.findByChannelAndRecipient(
            SubscriberChannelType.WHATSAPP,
            recipient,
        )
        if (existingChannel != null) {
            return SubscriberSeedResult(created = false, channel = existingChannel, skippedReason = "channel-exists")
        }

        val subscriber = subscriberRepository.save(
            SubscriberRecord(
                displayName = properties.whatsapp.defaultSubscriberDisplayName.ifBlank { "Airwallex FYI Subscriber" },
                status = SubscriberStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val channel = subscriberChannelRepository.save(
            SubscriberChannelRecord(
                subscriberId = subscriber.identifier(),
                channel = SubscriberChannelType.WHATSAPP,
                recipient = recipient,
                status = SubscriberStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            ),
        )

        return SubscriberSeedResult(created = true, subscriber = subscriber, channel = channel)
    }
}

data class SubscriberSeedResult(
    val created: Boolean,
    val subscriber: SubscriberRecord? = null,
    val channel: SubscriberChannelRecord? = null,
    val skippedReason: String? = null,
)
