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
    fun seedDefaultSubscriberIfConfigured(now: Instant = Instant.now()): Boolean {
        val recipient = properties.whatsapp.to.trim()
        if (recipient.isBlank()) return false

        val existingChannel = subscriberChannelRepository.findByChannelAndRecipient(
            SubscriberChannelType.WHATSAPP,
            recipient,
        )
        if (existingChannel != null) return false

        val subscriber = subscriberRepository.save(
            SubscriberRecord(
                displayName = DEFAULT_DISPLAY_NAME,
                status = SubscriberStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            ),
        )
        subscriberChannelRepository.save(
            SubscriberChannelRecord(
                subscriberId = subscriber.identifier(),
                channel = SubscriberChannelType.WHATSAPP,
                recipient = recipient,
                status = SubscriberStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            ),
        )

        return true
    }

    private companion object {
        const val DEFAULT_DISPLAY_NAME = "Airwallex FYI Subscriber"
    }
}
