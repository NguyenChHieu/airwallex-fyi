package com.airwallexfyi.digests

import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.notifications.NotificationResult
import com.airwallexfyi.notifications.NotificationStatus
import com.airwallexfyi.notifications.TelegramNotifier
import com.airwallexfyi.notifications.WhatsAppAlertPayload
import com.airwallexfyi.notifications.WhatsAppNotifier
import com.airwallexfyi.subscribers.SubscriberChannelRecord
import com.airwallexfyi.subscribers.SubscriberChannelRepository
import com.airwallexfyi.subscribers.SubscriberChannelType
import com.airwallexfyi.subscribers.SubscriberStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

@Service
class DailyDigestService(
    private val properties: AppProperties,
    private val subscriberChannelRepository: SubscriberChannelRepository,
    private val digestDeliveryRepository: DigestDeliveryRepository,
    private val digestDeliveryPostRepository: DigestDeliveryPostRepository,
    private val digestEligibilityService: DigestEligibilityService,
    private val dailyDigestFormatter: DailyDigestFormatter,
    private val whatsAppNotifier: WhatsAppNotifier,
    private val telegramNotifier: TelegramNotifier,
) {
    fun sendDailyDigests(now: Instant = Instant.now()): DailyDigestRunResult {
        val localDate = LocalDate.ofInstant(now, ZoneId.of(properties.digest.timeZone))
        val counters = DailyDigestCounters()
        val channels = SUPPORTED_CHANNELS
            .flatMap { channel ->
                subscriberChannelRepository.findByChannelAndStatusOrderByCreatedAtAsc(channel, SubscriberStatus.ACTIVE)
            }
            .sortedBy { it.createdAt }

        channels.forEach { subscriberChannel ->
            sendForChannel(subscriberChannel, localDate, now, counters)
        }

        return counters.toResult()
    }

    private fun sendForChannel(
        subscriberChannel: SubscriberChannelRecord,
        localDate: LocalDate,
        now: Instant,
        counters: DailyDigestCounters,
    ) {
        val existing = digestDeliveryRepository.findBySubscriberChannelIdAndLocalDate(
            subscriberChannel.identifier(),
            localDate,
        )
        if (existing != null) {
            if (existing.status != DigestDeliveryStatus.FAILED) {
                counters.skippedDuplicateCount += 1
                counters.addDeliverySample("${subscriberChannel.recipient} ${existing.messageType} ${DigestDeliveryStatus.SKIPPED_DUPLICATE}")
                return
            }
            digestDeliveryPostRepository.deleteAll(
                digestDeliveryPostRepository.findByDigestDeliveryIdOrderByDisplayOrderAsc(existing.identifier()),
            )
            digestDeliveryRepository.delete(existing)
        }

        val lastSuccessfulDelivery = digestDeliveryRepository.findMostRecentSuccessfulDelivery(subscriberChannel.identifier())
        val since = lastSuccessfulDelivery?.sentAt ?: lastSuccessfulDelivery?.attemptedAt
        val eligiblePosts = digestEligibilityService.findEligibleSummariesSince(since)
        val messageType = if (eligiblePosts.isEmpty()) DigestMessageType.NO_CHANGES else DigestMessageType.DIGEST
        val payload = if (eligiblePosts.isEmpty()) {
            dailyDigestFormatter.formatNoChanges(subscriberChannel.recipient)
        } else {
            dailyDigestFormatter.formatDigest(eligiblePosts, subscriberChannel.recipient, localDate)
        }

        val delivery = try {
            reserveDelivery(subscriberChannel, localDate, messageType, payload, now)
        } catch (_: DataIntegrityViolationException) {
            counters.skippedDuplicateCount += 1
            counters.addDeliverySample("${subscriberChannel.recipient} $messageType ${DigestDeliveryStatus.SKIPPED_DUPLICATE}")
            return
        }

        val notificationResult = try {
            sendNotification(subscriberChannel, payload)
        } catch (ex: RuntimeException) {
            NotificationResult(
                status = NotificationStatus.FAILED,
                payloadPreview = payload.preview,
                errorMessage = ex.sanitizedReason(),
                twilioCalled = false,
            )
        }

        val deliveryStatus = notificationResult.status.name
        val sentAt = if (notificationResult.status in NON_SENT_STATUSES) null else now
        digestDeliveryRepository.updateAfterAttempt(
            id = delivery.identifier(),
            status = deliveryStatus,
            providerMessageId = notificationResult.providerMessageId,
            errorMessage = notificationResult.errorMessage,
            sentAt = sentAt,
            updatedAt = now,
        )

        if (eligiblePosts.isNotEmpty()) {
            eligiblePosts.forEachIndexed { index, item ->
                digestDeliveryPostRepository.save(
                    DigestDeliveryPostRecord(
                        digestDeliveryId = delivery.identifier(),
                        postId = item.post.identifier(),
                        summaryId = item.summary.identifier(),
                        displayOrder = index,
                        createdAt = now,
                    ),
                )
            }
        }

        counters.twilioCallsTriggered = counters.twilioCallsTriggered || notificationResult.twilioCalled
        counters.addPayloadSample(notificationResult.payloadPreview)
        counters.addDeliverySample("${subscriberChannel.recipient} $messageType $deliveryStatus")
        when {
            notificationResult.status == NotificationStatus.FAILED -> {
                counters.failedCount += 1
                counters.addErrorSample("${subscriberChannel.recipient}: ${notificationResult.errorMessage ?: "delivery failed"}")
            }
            notificationResult.status == NotificationStatus.SKIPPED -> Unit
            messageType == DigestMessageType.NO_CHANGES -> counters.noChangeCount += 1
            else -> counters.digestSentCount += 1
        }
    }

    private fun reserveDelivery(
        subscriberChannel: SubscriberChannelRecord,
        localDate: LocalDate,
        messageType: String,
        payload: WhatsAppAlertPayload,
        now: Instant,
    ): DigestDeliveryRecord =
        digestDeliveryRepository.save(
            DigestDeliveryRecord(
                subscriberChannelId = subscriberChannel.identifier(),
                localDate = localDate,
                messageType = messageType,
                status = DigestDeliveryStatus.PENDING,
                recipient = subscriberChannel.recipient,
                channel = subscriberChannel.channel,
                payloadPreview = payload.preview,
                attemptedAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )

    private fun sendNotification(subscriberChannel: SubscriberChannelRecord, payload: WhatsAppAlertPayload): NotificationResult =
        when (subscriberChannel.channel) {
            SubscriberChannelType.WHATSAPP -> whatsAppNotifier.send(payload)
            SubscriberChannelType.TELEGRAM -> telegramNotifier.send(payload)
            else -> NotificationResult(
                status = NotificationStatus.FAILED,
                payloadPreview = payload.preview,
                errorMessage = "Unsupported subscriber channel: ${subscriberChannel.channel}",
                twilioCalled = false,
            )
        }

    private fun Throwable.sanitizedReason(): String =
        (message ?: javaClass.simpleName).lineSequence().firstOrNull()?.take(ERROR_LIMIT) ?: javaClass.simpleName

    private class DailyDigestCounters {
        var digestSentCount: Int = 0
        var noChangeCount: Int = 0
        var skippedDuplicateCount: Int = 0
        var failedCount: Int = 0
        var twilioCallsTriggered: Boolean = false
        private val deliverySamples = mutableListOf<String>()
        private val errorSamples = mutableListOf<String>()
        private val payloadSamples = mutableListOf<String>()

        fun addDeliverySample(value: String) = deliverySamples.addBounded(value)

        fun addErrorSample(value: String) = errorSamples.addBounded(value)

        fun addPayloadSample(value: String) = payloadSamples.addBounded(value)

        fun toResult(): DailyDigestRunResult = DailyDigestRunResult(
            digestSentCount = digestSentCount,
            noChangeCount = noChangeCount,
            skippedDuplicateCount = skippedDuplicateCount,
            failedCount = failedCount,
            sampleDeliveries = deliverySamples.toList(),
            sampleErrors = errorSamples.toList(),
            samplePayloads = payloadSamples.toList(),
            twilioCallsTriggered = twilioCallsTriggered,
        )
    }

    private companion object {
        const val ERROR_LIMIT = 240
        const val SAMPLE_LIMIT = 5
        val SUPPORTED_CHANNELS = listOf(SubscriberChannelType.WHATSAPP, SubscriberChannelType.TELEGRAM)
        val NON_SENT_STATUSES = setOf(NotificationStatus.FAILED, NotificationStatus.SKIPPED)

        fun MutableList<String>.addBounded(value: String) {
            if (size < SAMPLE_LIMIT) {
                add(value)
            }
        }
    }
}

data class DailyDigestRunResult(
    val digestSentCount: Int = 0,
    val noChangeCount: Int = 0,
    val skippedDuplicateCount: Int = 0,
    val failedCount: Int = 0,
    val sampleDeliveries: List<String> = emptyList(),
    val sampleErrors: List<String> = emptyList(),
    val samplePayloads: List<String> = emptyList(),
    val twilioCallsTriggered: Boolean = false,
)
