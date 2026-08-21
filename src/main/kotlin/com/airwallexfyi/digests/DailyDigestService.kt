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
import com.airwallexfyi.subscribers.TelegramChatAllowlist
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
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

        // Cheap per-channel checks (allowlist, already-sent-today) run first and skip
        // anyone who won't actually receive a digest. Only channels that pass need
        // eligible content, so the expensive read happens once for the whole run
        // instead of once per channel.
        val plans = channels.mapNotNull { subscriberChannel -> prepareChannel(subscriberChannel, localDate, now, counters) }

        if (plans.isNotEmpty()) {
            val floor = plans.minOf { it.since }
            val eligiblePosts = digestEligibilityService.findEligibleSummariesSince(floor)
            sendAll(plans, eligiblePosts, localDate, now, counters)
        }

        return counters.toResult()
    }

    // Sends run on a small bounded pool - see RestClientTelegramTransport's rate
    // limiter for why: Telegram's own per-bot-token throughput ceiling is the actual
    // bottleneck, not connection count, so a handful of workers is enough to saturate
    // it and more would just add contention without going faster. Each worker only
    // returns an outcome; nothing shared (counters, sample lists) is touched from a
    // worker thread - folding happens back on this thread, in plan order, so results
    // stay deterministic regardless of which send finishes first.
    private fun sendAll(
        plans: List<ChannelSendPlan>,
        eligiblePosts: List<DigestEligibleSummary>,
        localDate: LocalDate,
        now: Instant,
        counters: DailyDigestCounters,
    ) {
        val executor = Executors.newFixedThreadPool(properties.digest.sendConcurrency.coerceAtLeast(1))
        val futures = try {
            executor.invokeAll(plans.map { plan -> Callable { sendForChannel(plan, eligiblePosts, localDate, now) } })
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Digest fanout interrupted", ex)
        } finally {
            executor.shutdown()
        }

        futures.forEach { future ->
            val outcome = try {
                future.get()
            } catch (ex: ExecutionException) {
                // Unwrap so this still crashes with whatever RuntimeException
                // sendForChannel actually threw, same as the old sequential loop -
                // not a checked ExecutionException a caller's `catch (RuntimeException)`
                // wouldn't match.
                throw ex.cause ?: ex
            }
            counters.record(outcome)
        }
    }

    private fun prepareChannel(
        subscriberChannel: SubscriberChannelRecord,
        localDate: LocalDate,
        now: Instant,
        counters: DailyDigestCounters,
    ): ChannelSendPlan? {
        val existing = digestDeliveryRepository.findBySubscriberChannelIdAndLocalDate(
            subscriberChannel.identifier(),
            localDate,
        )
        if (!subscriberChannel.isAllowedByCurrentConfig()) {
            counters.skippedAccessCount += 1
            counters.addDeliverySample("${subscriberChannel.recipient} ACCESS ${DigestDeliveryStatus.SKIPPED}")
            return null
        }
        if (existing != null) {
            if (existing.status != DigestDeliveryStatus.FAILED) {
                counters.skippedDuplicateCount += 1
                counters.addDeliverySample("${subscriberChannel.recipient} ${existing.messageType} ${DigestDeliveryStatus.SKIPPED_DUPLICATE}")
                return null
            }
            digestDeliveryPostRepository.deleteAll(
                digestDeliveryPostRepository.findByDigestDeliveryIdOrderByDisplayOrderAsc(existing.identifier()),
            )
            digestDeliveryRepository.delete(existing)
        }

        val lastSuccessfulDelivery = digestDeliveryRepository.findMostRecentSuccessfulDelivery(subscriberChannel.identifier())
        // A channel with no prior successful delivery is brand new (or has never sent
        // successfully): bound its first digest to a recent window instead of the
        // entire summarized history - otherwise a fresh /start dumps every post ever
        // summarized, which is exactly the historical-spam this project's "no
        // historical spam" constraint rules out.
        val since = lastSuccessfulDelivery?.sentAt
            ?: lastSuccessfulDelivery?.attemptedAt
            ?: now.minus(FIRST_DIGEST_LOOKBACK)
        return ChannelSendPlan(subscriberChannel, since)
    }

    // Runs on a worker thread when called from sendAll's executor - must not touch
    // counters or any other shared mutable state directly. Everything it needs to
    // report back travels in the returned ChannelSendOutcome.
    private fun sendForChannel(
        plan: ChannelSendPlan,
        allEligiblePosts: List<DigestEligibleSummary>,
        localDate: LocalDate,
        now: Instant,
    ): ChannelSendOutcome {
        val subscriberChannel = plan.subscriberChannel
        // allEligiblePosts is already sorted; filtering preserves that order, so no
        // re-sort is needed for this channel's own (later-or-equal) watermark.
        val eligiblePosts = allEligiblePosts.filter { it.summary.createdAt.isAfter(plan.since) }
        val messageType = if (eligiblePosts.isEmpty()) DigestMessageType.NO_CHANGES else DigestMessageType.DIGEST
        val payload = if (eligiblePosts.isEmpty()) {
            dailyDigestFormatter.formatNoChanges(subscriberChannel.recipient)
        } else {
            dailyDigestFormatter.formatDigest(eligiblePosts, subscriberChannel.recipient, localDate)
        }

        val delivery = try {
            reserveDelivery(subscriberChannel, localDate, messageType, now)
        } catch (_: DataIntegrityViolationException) {
            return ChannelSendOutcome.DuplicateRace(subscriberChannel.recipient, messageType)
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

        return ChannelSendOutcome.Sent(
            recipient = subscriberChannel.recipient,
            messageType = messageType,
            notificationResult = notificationResult,
        )
    }

    private fun reserveDelivery(
        subscriberChannel: SubscriberChannelRecord,
        localDate: LocalDate,
        messageType: String,
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

    private fun SubscriberChannelRecord.isAllowedByCurrentConfig(): Boolean =
        channel != SubscriberChannelType.TELEGRAM ||
            TelegramChatAllowlist.allows(properties.telegram.allowedChatIds, recipient)

    private fun Throwable.sanitizedReason(): String =
        (message ?: javaClass.simpleName).lineSequence().firstOrNull()?.take(ERROR_LIMIT) ?: javaClass.simpleName

    private data class ChannelSendPlan(
        val subscriberChannel: SubscriberChannelRecord,
        val since: Instant,
    )

    private sealed interface ChannelSendOutcome {
        data class Sent(
            val recipient: String,
            val messageType: String,
            val notificationResult: NotificationResult,
        ) : ChannelSendOutcome

        data class DuplicateRace(
            val recipient: String,
            val messageType: String,
        ) : ChannelSendOutcome
    }

    private class DailyDigestCounters {
        var digestSentCount: Int = 0
        var noChangeCount: Int = 0
        var skippedDuplicateCount: Int = 0
        var skippedAccessCount: Int = 0
        var failedCount: Int = 0
        var twilioCallsTriggered: Boolean = false
        var telegramCallsTriggered: Boolean = false
        private val deliverySamples = mutableListOf<String>()
        private val errorSamples = mutableListOf<String>()
        private val payloadSamples = mutableListOf<String>()

        // Only ever called from the run's own thread, after every worker in the
        // batch has already finished (see sendAll) - never touched concurrently.
        fun record(outcome: ChannelSendOutcome) {
            when (outcome) {
                is ChannelSendOutcome.DuplicateRace -> {
                    skippedDuplicateCount += 1
                    addDeliverySample("${outcome.recipient} ${outcome.messageType} ${DigestDeliveryStatus.SKIPPED_DUPLICATE}")
                }
                is ChannelSendOutcome.Sent -> {
                    val result = outcome.notificationResult
                    twilioCallsTriggered = twilioCallsTriggered || result.twilioCalled
                    telegramCallsTriggered = telegramCallsTriggered || result.telegramCalled
                    addPayloadSample(result.payloadPreview)
                    addDeliverySample("${outcome.recipient} ${outcome.messageType} ${result.status.name}")
                    when {
                        result.status == NotificationStatus.FAILED -> {
                            failedCount += 1
                            addErrorSample("${outcome.recipient}: ${result.errorMessage ?: "delivery failed"}")
                        }
                        result.status == NotificationStatus.SKIPPED -> Unit
                        outcome.messageType == DigestMessageType.NO_CHANGES -> noChangeCount += 1
                        else -> digestSentCount += 1
                    }
                }
            }
        }

        fun addDeliverySample(value: String) = deliverySamples.addBounded(value)

        fun addErrorSample(value: String) = errorSamples.addBounded(value)

        fun addPayloadSample(value: String) = payloadSamples.addBounded(value)

        fun toResult(): DailyDigestRunResult = DailyDigestRunResult(
            digestSentCount = digestSentCount,
            noChangeCount = noChangeCount,
            skippedDuplicateCount = skippedDuplicateCount,
            skippedAccessCount = skippedAccessCount,
            failedCount = failedCount,
            sampleDeliveries = deliverySamples.toList(),
            sampleErrors = errorSamples.toList(),
            samplePayloads = payloadSamples.toList(),
            twilioCallsTriggered = twilioCallsTriggered,
            telegramCallsTriggered = telegramCallsTriggered,
        )
    }

    private companion object {
        const val ERROR_LIMIT = 240
        const val SAMPLE_LIMIT = 5
        val SUPPORTED_CHANNELS = listOf(SubscriberChannelType.WHATSAPP, SubscriberChannelType.TELEGRAM)
        val NON_SENT_STATUSES = setOf(NotificationStatus.FAILED, NotificationStatus.SKIPPED)
        val FIRST_DIGEST_LOOKBACK: Duration = Duration.ofHours(48)

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
    val skippedAccessCount: Int = 0,
    val failedCount: Int = 0,
    val sampleDeliveries: List<String> = emptyList(),
    val sampleErrors: List<String> = emptyList(),
    val samplePayloads: List<String> = emptyList(),
    val twilioCallsTriggered: Boolean = false,
    val telegramCallsTriggered: Boolean = false,
)
