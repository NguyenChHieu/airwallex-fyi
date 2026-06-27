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
        val whatsappSeeded = seedConfiguredChannel(
            channel = SubscriberChannelType.WHATSAPP,
            recipient = properties.whatsapp.to.trim(),
            now = now,
        )
        val telegramSeeded = seedConfiguredChannel(
            channel = SubscriberChannelType.TELEGRAM,
            recipient = properties.telegram.chatId.trim(),
            now = now,
        )

        return whatsappSeeded || telegramSeeded
    }

    private fun seedConfiguredChannel(channel: String, recipient: String, now: Instant): Boolean {
        if (recipient.isBlank()) return false

        val existingChannel = subscriberChannelRepository.findByChannelAndRecipient(
            channel,
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
                channel = channel,
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
