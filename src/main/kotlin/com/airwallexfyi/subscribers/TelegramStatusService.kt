package com.airwallexfyi.subscribers

import com.airwallexfyi.config.AppProperties
import com.airwallexfyi.digests.DigestDeliveryRepository
import com.airwallexfyi.posts.PostRecord
import com.airwallexfyi.posts.PostRepository
import java.time.Instant
import org.springframework.stereotype.Service

@Service
class TelegramStatusService(
    private val properties: AppProperties,
    private val subscriberChannelRepository: SubscriberChannelRepository,
    private val digestDeliveryRepository: DigestDeliveryRepository,
    private val postRepository: PostRepository,
) {
    fun formatStatus(chatId: String): String {
        val channel = subscriberChannelRepository.findByChannelAndRecipient(
            SubscriberChannelType.TELEGRAM,
            chatId,
        )
        val latestDigest = channel?.let {
            digestDeliveryRepository.findMostRecentSuccessfulDelivery(it.identifier())
        }
        val latestPost = latestDiscoveredPost()

        return buildString {
            appendLine("Airwallex FYI status")
            appendLine()
            appendLine("Subscription: ${channel?.status?.lowercase() ?: "inactive"}")
            appendLine("Latest digest: ${latestDigest?.statusLine() ?: "none yet"}")
            appendLine("Latest update seen: ${latestPost?.statusLine() ?: "none yet"}")
            appendLine("Mode: ${modeLine()}")
        }.trimEnd()
    }

    private fun latestDiscoveredPost(): PostRecord? = postRepository.findAll()
        .maxWithOrNull(
            compareBy<PostRecord> { it.discoveredAt }
                .thenBy { it.createdAt },
        )

    private fun com.airwallexfyi.digests.DigestDeliveryRecord.statusLine(): String =
        "$status $messageType on $localDate"

    private fun PostRecord.statusLine(): String {
        val titleText = title?.takeIf { it.isNotBlank() } ?: url.substringAfterLast('/').ifBlank { url }
        val seenAt = publishedAt ?: discoveredAt
        return "${titleText.cleanInline()} (${sourceType.lowercase()}, ${seenAt.toStatusDate()})"
    }

    private fun modeLine(): String =
        if (properties.scheduler.enabled) {
            "app scheduler enabled"
        } else {
            "Render webhook + GitHub Actions daily"
        }

    private fun Instant.toStatusDate(): String = toString().substringBefore('T')

    private fun String.cleanInline(): String = trim().replace(WHITESPACE, " ")

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
