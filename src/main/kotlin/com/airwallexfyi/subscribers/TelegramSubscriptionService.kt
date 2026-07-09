package com.airwallexfyi.subscribers

import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.digests.LatestUpdatesService
import com.airwallexfyi.notifications.MessageBodyLimits
import com.airwallexfyi.notifications.TelegramChat
import com.airwallexfyi.notifications.TelegramTransport
import com.airwallexfyi.notifications.TelegramUpdate
import com.airwallexfyi.state.AppStateRepository
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TelegramSubscriptionService(
    private val properties: AppProperties,
    private val telegramTransport: TelegramTransport,
    private val appStateRepository: AppStateRepository,
    private val subscriberRepository: SubscriberRepository,
    private val subscriberChannelRepository: SubscriberChannelRepository,
    private val latestUpdatesService: LatestUpdatesService,
    private val telegramStatusService: TelegramStatusService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun syncSubscriptions(now: Instant = Instant.now()): TelegramSubscriptionSyncResult {
        if (properties.dryRun || properties.telegram.botToken.isBlank() || properties.telegram.webhookSecret.isNotBlank()) {
            return TelegramSubscriptionSyncResult(skipped = true)
        }

        val offset = nextOffset()
        logger.info("Telegram subscription getUpdates started: offset={}", offset)
        val updates = try {
            telegramTransport.getUpdates(
                botToken = properties.telegram.botToken,
                offset = offset,
            )
        } catch (ex: RuntimeException) {
            return TelegramSubscriptionSyncResult(
                failedCount = 1,
                sampleErrors = listOf("Telegram subscription sync failed: ${ex.sanitizedReason()}"),
            )
        }
        logger.info("Telegram subscription getUpdates completed: updates={}", updates.size)

        val counters = TelegramSubscriptionCounters()
        updates.sortedBy { it.updateId }.forEach { update ->
            counters.processedCount += 1
            counters.lastUpdateId = update.updateId
            processUpdate(update, now, counters)
        }

        counters.lastUpdateId?.let { lastUpdateId ->
            appStateRepository.putValue(STATE_KEY_LAST_UPDATE_ID, lastUpdateId.toString(), now)
        }
        return counters.toResult()
    }

    @Synchronized
    fun processWebhookUpdate(update: TelegramUpdate, now: Instant = Instant.now()): TelegramSubscriptionSyncResult {
        if (properties.dryRun || properties.telegram.botToken.isBlank()) {
            return TelegramSubscriptionSyncResult(skipped = true)
        }

        val lastUpdateId = lastUpdateId()
        if (lastUpdateId != null && update.updateId <= lastUpdateId) {
            return TelegramSubscriptionSyncResult()
        }

        val counters = TelegramSubscriptionCounters()
        counters.processedCount += 1
        counters.lastUpdateId = update.updateId
        processUpdate(update, now, counters)
        appStateRepository.putValue(STATE_KEY_LAST_UPDATE_ID, update.updateId.toString(), now)
        return counters.toResult()
    }

    private fun nextOffset(): Long? =
        lastUpdateId()
            ?.plus(1)

    private fun lastUpdateId(): Long? =
        appStateRepository.findValue(STATE_KEY_LAST_UPDATE_ID)?.toLongOrNull()

    private fun processUpdate(update: TelegramUpdate, now: Instant, counters: TelegramSubscriptionCounters) {
        val message = update.message ?: return
        val command = message.text.toCommand() ?: return
        val chat = message.chat
        if (!chat.isAllowed()) {
            sendConfirmation(
                chat.id.toString(),
                "Airwallex FYI is currently private. Ask the owner to add this chat ID: ${chat.id}",
                counters,
            )
            return
        }
        when (command) {
            TelegramCommand.START -> {
                if (activate(chat, now)) counters.subscribedCount += 1
                sendConfirmation(
                    chat.id.toString(),
                    "You're subscribed to Airwallex FYI. Send /stop anytime to unsubscribe.",
                    counters,
                )
            }
            TelegramCommand.STOP -> {
                if (deactivate(chat, now)) counters.unsubscribedCount += 1
                sendConfirmation(
                    chat.id.toString(),
                    "You're unsubscribed from Airwallex FYI. Send /start to subscribe again.",
                    counters,
                )
            }
            TelegramCommand.HELP -> sendConfirmation(
                chat.id.toString(),
                "Airwallex FYI commands: /start subscribes, /stop unsubscribes, /latest shows recent updates, /status checks bot state.",
                counters,
            )
            TelegramCommand.LATEST -> sendConfirmation(
                chat.id.toString(),
                latestUpdatesService.formatLatest(maxBodyChars = MessageBodyLimits.TELEGRAM),
                counters,
            )
            TelegramCommand.STATUS -> sendConfirmation(
                chat.id.toString(),
                telegramStatusService.formatStatus(chat.id.toString()),
                counters,
            )
        }
    }

    private fun activate(chat: TelegramChat, now: Instant): Boolean {
        val recipient = chat.id.toString()
        val existing = subscriberChannelRepository.findByChannelAndRecipient(
            SubscriberChannelType.TELEGRAM,
            recipient,
        )
        if (existing != null) {
            if (existing.status != SubscriberStatus.ACTIVE) {
                subscriberChannelRepository.updateStatus(existing.identifier(), SubscriberStatus.ACTIVE, now)
                return true
            }
            return false
        }

        val subscriber = subscriberRepository.save(
            SubscriberRecord(
                displayName = chat.displayName(),
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
                status = SubscriberStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return true
    }

    private fun deactivate(chat: TelegramChat, now: Instant): Boolean {
        val existing = subscriberChannelRepository.findByChannelAndRecipient(
            SubscriberChannelType.TELEGRAM,
            chat.id.toString(),
        ) ?: return false
        if (existing.status == SubscriberStatus.INACTIVE) return false

        subscriberChannelRepository.updateStatus(existing.identifier(), SubscriberStatus.INACTIVE, now)
        return true
    }

    private fun sendConfirmation(chatId: String, message: String, counters: TelegramSubscriptionCounters) {
        try {
            telegramTransport.sendMessage(
                botToken = properties.telegram.botToken,
                chatId = chatId,
                body = message,
            )
        } catch (ex: RuntimeException) {
            counters.failedCount += 1
            counters.addError("Telegram confirmation failed for $chatId: ${ex.sanitizedReason()}")
        }
    }

    private fun String?.toCommand(): TelegramCommand? {
        val command = this
            ?.trim()
            ?.substringBefore(' ')
            ?.substringBefore('@')
            ?.lowercase()
        return when (command) {
            "/start" -> TelegramCommand.START
            "/stop" -> TelegramCommand.STOP
            "/help" -> TelegramCommand.HELP
            "/latest" -> TelegramCommand.LATEST
            "/status" -> TelegramCommand.STATUS
            else -> null
        }
    }

    private fun TelegramChat.isAllowed(): Boolean {
        return TelegramChatAllowlist.allows(properties.telegram.allowedChatIds, id.toString())
    }

    private fun TelegramChat.displayName(): String =
        when {
            !username.isNullOrBlank() -> "@$username"
            listOfNotNull(firstName, lastName).joinToString(" ").isNotBlank() -> listOfNotNull(firstName, lastName)
                .joinToString(" ")
            else -> "Telegram $id"
        }

    private fun Throwable.sanitizedReason(): String {
        val raw = (message ?: javaClass.simpleName).lineSequence().firstOrNull()?.take(ERROR_LIMIT) ?: javaClass.simpleName
        val secret = properties.telegram.botToken
        return if (secret.isBlank()) raw else raw.replace(secret, "[redacted]")
    }

    private class TelegramSubscriptionCounters {
        var processedCount = 0
        var subscribedCount = 0
        var unsubscribedCount = 0
        var failedCount = 0
        var lastUpdateId: Long? = null
        private val errors = mutableListOf<String>()

        fun addError(value: String) {
            if (errors.size < SAMPLE_LIMIT) errors += value
        }

        fun toResult(): TelegramSubscriptionSyncResult = TelegramSubscriptionSyncResult(
            processedCount = processedCount,
            subscribedCount = subscribedCount,
            unsubscribedCount = unsubscribedCount,
            failedCount = failedCount,
            sampleErrors = errors.toList(),
        )
    }

    private enum class TelegramCommand {
        START,
        STOP,
        HELP,
        LATEST,
        STATUS,
    }

    private companion object {
        const val STATE_KEY_LAST_UPDATE_ID = "telegram.last_update_id"
        const val ERROR_LIMIT = 240
        const val SAMPLE_LIMIT = 5
    }
}

data class TelegramSubscriptionSyncResult(
    val processedCount: Int = 0,
    val subscribedCount: Int = 0,
    val unsubscribedCount: Int = 0,
    val failedCount: Int = 0,
    val skipped: Boolean = false,
    val sampleErrors: List<String> = emptyList(),
)
