package com.airwallexfyi.notifications

import com.airwallexfyi.posts.PostRecord
import java.time.Instant
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "airwallex-fyi", name = ["dry-run"], havingValue = "true", matchIfMissing = true)
class DryRunWhatsAppNotifier(
    private val notificationAttemptRepository: NotificationAttemptRepository,
) : WhatsAppNotifier {
    override fun send(post: PostRecord, payload: WhatsAppAlertPayload): NotificationResult {
        val now = Instant.now()
        notificationAttemptRepository.save(
            NotificationAttemptRecord(
                postId = post.identifier(),
                channel = payload.channel,
                recipient = payload.recipient,
                status = NotificationStatus.DRY_RUN.name,
                attemptedAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )

        return NotificationResult(
            status = NotificationStatus.DRY_RUN,
            payloadPreview = payload.preview,
            twilioCalled = false,
        )
    }
}
